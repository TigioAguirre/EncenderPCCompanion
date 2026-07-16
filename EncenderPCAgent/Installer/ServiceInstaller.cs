using System.ComponentModel;
using System.Diagnostics;
using System.Security.Principal;
using Microsoft.Win32;

namespace EncenderPCAgent.Installer;

/// <summary>
/// Todo lo que antes hacía <c>install.ps1</c> / <c>uninstall.ps1</c> a mano
/// (registrar el servicio de Windows, arrancarlo, reiniciarlo), pero
/// invocado directamente desde el propio .exe. Así, instalar el agente
/// pasa a ser "doble click y aceptar el permiso de Administrador" en vez
/// de requerir PowerShell o el SDK de .NET en la PC final.
///
/// Usa <c>sc.exe</c> (viene incluido con Windows) para crear/borrar el
/// servicio: no depende de ningún paquete extra.
/// </summary>
public static class ServiceInstaller
{
    public const string ServiceName = "EncenderPCAgent";
    private const string DisplayName = "EncenderPC Companion Agent";
    private const string Description = "Reporta a EncenderPCCompanion que esta PC esta encendida.";

    public static bool IsAdministrator()
    {
        using var identity = WindowsIdentity.GetCurrent();
        var principal = new WindowsPrincipal(identity);
        return principal.IsInRole(WindowsBuiltInRole.Administrator);
    }

    /// <summary>
    /// Vuelve a lanzar este mismo .exe pidiendo elevación (dispara el
    /// cuadro de UAC de Windows). Devuelve false si no se pudo lanzar
    /// (por ejemplo, el usuario le dijo "No" al cuadro de permisos).
    /// </summary>
    public static bool RelaunchElevated(IEnumerable<string> arguments)
    {
        try
        {
            var exePath = Environment.ProcessPath
                ?? Process.GetCurrentProcess().MainModule?.FileName
                ?? throw new InvalidOperationException("No se pudo determinar la ruta del ejecutable actual.");

            var psi = new ProcessStartInfo(exePath)
            {
                UseShellExecute = true,
                Verb = "runas",
                WorkingDirectory = AppContext.BaseDirectory
            };
            foreach (var arg in arguments)
            {
                psi.ArgumentList.Add(arg);
            }

            using var process = Process.Start(psi);
            return process is not null;
        }
        catch (Win32Exception)
        {
            // ERROR_CANCELLED: el usuario canceló el cuadro de UAC.
            return false;
        }
    }

    /// <summary>
    /// Registra (o reemplaza) el servicio de Windows apuntando al .exe
    /// que se está ejecutando en este momento. Si ya existía un servicio
    /// con este nombre lo borra primero, así queda apuntando siempre a la
    /// carpeta actual (por ejemplo, si el usuario movió la carpeta del
    /// agente, esto lo corrige solo). Requiere permisos de Administrador.
    /// </summary>
    public static bool RegisterService(out string message)
    {
        var exePath = Environment.ProcessPath
            ?? Process.GetCurrentProcess().MainModule?.FileName;

        if (string.IsNullOrEmpty(exePath))
        {
            message = "No se pudo determinar la ruta del ejecutable actual.";
            return false;
        }

        if (ServiceExists())
        {
            StopService();
            RunSc("delete", ServiceName);
            Thread.Sleep(1000);
        }

        // FIX 1 (histórico, REVERTIDO): se había puesto "delayed-auto" para
        // reducir el Error 1053 en arranques muy cargados de CPU/disco.
        //
        // BUG ENCONTRADO: "delayed-auto" NO es "arranca un ratito después".
        // Windows recién dispara el temporizador de inicio retrasado cuando
        // considera terminado el arranque del sistema (los servicios
        // "Automatic" normales ya están corriendo), y encima corre en un
        // hilo de fondo de PRIORIDAD BAJA que cede ante cualquier otra cosa
        // que esté usando CPU/disco. En una PC con arranque en frío real
        // (Inicio rápido desactivado), eso empuja el arranque del servicio
        // minutos hacia adelante — en la práctica, hasta después de que el
        // usuario ya inició sesión (perfil, apps de inicio, antivirus, etc.
        // compitiendo justo en ese momento). Por eso el aviso de "online"
        // parecía depender de loguearse, cuando en realidad dependía de esta
        // política de arranque.
        //
        // En una PC CON Inicio rápido, "Apagar" en realidad hiberna la
        // sesión 0 (donde viven los servicios) en vez de apagarla de
        // verdad: al prender, Windows resume ese estado ya hibernado, así
        // que el servicio (delayed-auto o no) ya estaba "corriendo" desde
        // antes del hibernado — por eso ahí sí se veía instantáneo (además,
        // EncenderPcWindowsService.OnPowerEvent(ResumeSuspend/...) manda
        // "online" apenas se resume, sin esperar al próximo heartbeat).
        //
        // El riesgo original de Error 1053 que "delayed-auto" buscaba
        // evitar ya está cubierto por otro lado: el ServicesPipeTimeout de
        // 60s (FIX 4, más abajo) le da al proceso de sobra para
        // auto-extraerse y pasar el escaneo del antivirus en el primer
        // arranque, sin necesidad de retrasar CUÁNDO arranca. Con "auto" a
        // secas el servicio arranca apenas termina el grupo de servicios
        // normales del sistema (mucho antes de la pantalla de login), y la
        // dependencia de Tcpip (FIX 2) sigue asegurando que no arranque
        // antes de que el stack de red esté cargado.
        var create = RunSc(
            "create", ServiceName,
            "binPath=", exePath,
            "start=", "auto",
            "DisplayName=", DisplayName);

        if (create.ExitCode != 0)
        {
            message = $"No se pudo registrar el servicio (sc create devolvió {create.ExitCode}): {create.StdErr}{create.StdOut}".Trim();
            return false;
        }

        RunSc("description", ServiceName, Description);

        // FIX 2: Añadimos una dependencia estricta de red. El servicio no intentará 
        // arrancar hasta que el stack TCP/IP esté completamente inicializado.
        RunSc("config", ServiceName, "depend=", "Tcpip");

        // Si el proceso del servicio muere solo (crash), que Windows lo
        // reintente en vez de dejar la PC marcada "offline" hasta el
        // próximo reinicio manual.
        RunSc("failure", ServiceName, "reset=", "86400", "actions=", "restart/5000/restart/5000/restart/5000");

        // FIX 3: Ajuste del timeout de apagado en el Registro de Windows.
        //
        // BUG ENCONTRADO: esto se escribía antes en
        // "SYSTEM\CurrentControlSet\Services\{ServiceName}\WaitToKillServiceTimeout".
        // Esa clave ahí no significa nada para Windows: WaitToKillServiceTimeout
        // NO es una configuración por-servicio, es un valor GLOBAL que vive en
        // "SYSTEM\CurrentControlSet\Control" y se aplica a todos los servicios
        // (y apps) por igual al apagar la PC. Como nunca se tocaba la clave real,
        // Windows seguía usando su timeout por defecto (bastante más corto que
        // los 15s que este servicio necesita para el POST de "offline" a
        // Firebase) y mataba el proceso antes de que StopAsync terminara.
        // Resultado: el estado "offline" no se veía en el momento del apagado,
        // sino recién ~90s después (cuando Companion asume la PC apagada por
        // falta de heartbeats, en vez de por el aviso explícito).
        //
        // Es una clave GLOBAL (afecta el apagado de toda la PC, no solo este
        // servicio), así que igual que con ServicesPipeTimeout la subimos con
        // cuidado y solo si el valor actual es menor al que necesitamos.
        try
        {
            using var key = Registry.LocalMachine.OpenSubKey(
                @"SYSTEM\CurrentControlSet\Control", true);
            if (key != null)
            {
                var current = key.GetValue("WaitToKillServiceTimeout");
                var currentMs = ParseTimeoutMs(current);
                const int desiredMs = 20000; // 20s: cubre los 15s que le pedimos a StopAsync, con margen.
                if (currentMs < desiredMs)
                {
                    key.SetValue("WaitToKillServiceTimeout", desiredMs.ToString(), RegistryValueKind.String);
                }
            }
        }
        catch
        {
            // Si no se puede escribir esta clave, seguimos con el timeout por
            // defecto de Windows; no es motivo para cortar la instalación.
        }

        // FIX 4: Le damos más margen al arranque del PROCESO en sí (distinto
        // del ShutdownTimeout de FIX 2/3, que es para el StopAsync). Este
        // .exe es self-contained + single-file: la primera vez que arranca
        // tiene que auto-extraer sus librerías nativas a una carpeta temporal,
        // y encima suele ser escaneado por el antivirus al ejecutarse. Eso
        // puede tardar más de los ~30 segundos que Windows le da por defecto
        // a un servicio nuevo para conectarse al Service Control Manager —
        // si se pasa de ese margen, Windows tira exactamente el:
        //   "Error 1053: el servicio no respondió a tiempo la solicitud de
        //    inicio o control."
        // ServicesPipeTimeout es una clave GLOBAL (afecta a todos los
        // servicios de la PC, no solo a este), así que la subimos con
        // cuidado y solo si no hay ya un valor mayor puesto por otra app.
        try
        {
            using var key = Registry.LocalMachine.OpenSubKey(
                @"SYSTEM\CurrentControlSet\Control", true);
            if (key != null)
            {
                var current = key.GetValue("ServicesPipeTimeout");
                var currentMs = current is int i ? i : 0;
                const int desiredMs = 60000; // 60s en vez de los ~30s por defecto
                if (currentMs < desiredMs)
                {
                    key.SetValue("ServicesPipeTimeout", desiredMs, RegistryValueKind.DWord);
                }
            }
        }
        catch
        {
            // Si no se puede escribir esta clave (raro, ya corremos como
            // Administrador), seguimos igual con el timeout por defecto de
            // Windows; no es motivo para cortar la instalación.
        }

        message = "Servicio registrado correctamente (inicio automático normal, con espera de red).";
        return true;
    }

    public static bool ServiceExists()
    {
        var result = RunSc("query", ServiceName);
        return result.ExitCode == 0;
    }

    public static bool IsServiceRunning()
    {
        var result = RunSc("query", ServiceName);
        return result.ExitCode == 0 && result.StdOut.Contains("RUNNING", StringComparison.OrdinalIgnoreCase);
    }

    // FIX 5: 45s en vez de 15s. El .exe es self-contained + single-file:
    // la PRIMERA vez que arranca el servicio tiene que auto-extraer sus
    // librerías nativas a una carpeta temporal, y encima Windows Defender
    // (u otro antivirus) suele escanearlo completo en ese primer arranque.
    // Con 15s de espera, esta clase reportaba "no se pudo confirmar que
    // arrancó" (IsServiceRunning() == false) aunque el servicio SÍ
    // arrancaba unos segundos más tarde — eso hacía pensar que había que
    // reiniciar Windows para que "prendiera", cuando en realidad solo
    // hacía falta esperar un poco más al primer arranque.
    private const int DefaultStartStopTimeoutSeconds = 45;

    public static void StartService()
    {
        RunSc("start", ServiceName);
        WaitForState(running: true, DefaultStartStopTimeoutSeconds);
    }

    public static void StopService()
    {
        if (!IsServiceRunning()) return;
        RunSc("stop", ServiceName);
        WaitForState(running: false, DefaultStartStopTimeoutSeconds);
    }

    /// <summary>
    /// Espera (sin tocar el estado del servicio) a que quede confirmado
    /// como RUNNING. Se usa después de un <see cref="StartService"/> que
    /// ya se disparó en otro lado (p. ej. dentro de <c>PairingService</c>)
    /// para no volver a mandar "sc start"/"sc stop" encima de un arranque
    /// que todavía está en curso (eso es lo que antes lo interrumpía).
    /// </summary>
    public static bool WaitUntilRunning(int timeoutSeconds = DefaultStartStopTimeoutSeconds)
    {
        WaitForState(running: true, timeoutSeconds);
        return IsServiceRunning();
    }

    /// <summary>
    /// Para y vuelve a arrancar el servicio para que relea
    /// <c>device.json</c>. Esto es lo que hace que, al re-vincular la
    /// misma PC, el cambio se refleje sin tener que reiniciar Windows
    /// entero (antes se quedaba mostrando la PC como "apagada" hasta el
    /// próximo reinicio).
    /// </summary>
    public static void RestartService()
    {
        if (IsServiceRunning())
        {
            StopService();
        }
        StartService();
    }

    /// <summary>
    /// Igual que <see cref="RestartService"/>, pero no hace nada (ni
    /// falla) si el servicio todavía no está instalado — por ejemplo, si
    /// alguien corrió "pair" a mano sin haber instalado el servicio.
    /// </summary>
    public static void RestartServiceIfInstalled()
    {
        if (!ServiceExists()) return;
        RestartService();
    }

    public static void UninstallService()
    {
        if (!ServiceExists()) return;
        StopService();
        RunSc("delete", ServiceName);
    }

    private static void WaitForState(bool running, int timeoutSeconds = 15)
    {
        var deadline = DateTime.UtcNow.AddSeconds(timeoutSeconds);
        while (DateTime.UtcNow < deadline)
        {
            if (IsServiceRunning() == running) return;
            Thread.Sleep(500);
        }
    }

    /// <summary>
    /// WaitToKillServiceTimeout históricamente se guarda como REG_SZ (texto),
    /// pero algunas herramientas/GPOs lo dejan como REG_DWORD. Soportamos
    /// ambos para no pisar un valor más alto que haya puesto otra cosa.
    /// </summary>
    private static int ParseTimeoutMs(object? value) => value switch
    {
        int i => i,
        string s when int.TryParse(s, out var parsed) => parsed,
        _ => 0
    };

    private static (int ExitCode, string StdOut, string StdErr) RunSc(params string[] arguments)
    {
        var psi = new ProcessStartInfo("sc.exe")
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };
        foreach (var arg in arguments)
        {
            psi.ArgumentList.Add(arg);
        }

        using var process = Process.Start(psi)!;
        var stdout = process.StandardOutput.ReadToEnd();
        var stderr = process.StandardError.ReadToEnd();
        process.WaitForExit();
        return (process.ExitCode, stdout, stderr);
    }
}