using EncenderPCAgent.Config;
using EncenderPCAgent.Firebase;
using EncenderPCAgent.Pairing;
using Microsoft.Extensions.Configuration;

namespace EncenderPCAgent.Installer;

/// <summary>
/// Lo que corre cuando un usuario sin conocimientos de CLI hace doble
/// click en EncenderPCAgent.exe. Reemplaza el flujo anterior de
/// "install.ps1 -> pair -> Start-Service", que requería PowerShell, el
/// SDK de .NET y saber usar la consola.
///
/// Todo en un solo doble click:
///   1) Si no está corriendo como Administrador, se relanza pidiendo UAC.
///   2) Registra (o repara) el servicio de Windows.
///   3) Si la PC no está emparejada, pide el código de 6 dígitos.
///   4) Arranca (o reinicia) el servicio para que quede en línea ya mismo.
/// </summary>
public static class SetupWizard
{
    public static async Task<int> RunAsync(string[] args)
    {
        Console.Title = "EncenderPC - Configuración";
        Console.WriteLine("=========================================");
        Console.WriteLine(" EncenderPC Companion - Agente para PC");
        Console.WriteLine("=========================================");
        Console.WriteLine();

        if (!ServiceInstaller.IsAdministrator())
        {
            Console.WriteLine("Este programa necesita permisos de Administrador para instalarse");
            Console.WriteLine("como servicio de Windows. Te va a aparecer un cuadro pidiendo permiso...");
            Console.WriteLine();

            // Reintenta este mismo modo (sin argumentos = wizard) ya elevado.
            var relaunched = ServiceInstaller.RelaunchElevated(args);
            if (!relaunched)
            {
                Console.WriteLine("No se pudo continuar sin permisos de Administrador.");
                Console.WriteLine("Volvé a abrir el programa y aceptá el cuadro de UAC.");
                Console.WriteLine();
                Console.WriteLine("Presioná ENTER para salir...");
                Console.ReadLine();
                return 1;
            }

            // El proceso elevado sigue solo desde acá; este (el original,
            // sin permisos) ya cumplió su función lanzándolo.
            return 0;
        }

        Console.WriteLine("Paso 1/3: Instalando el servicio de Windows...");
        if (!ServiceInstaller.RegisterService(out var registerMessage))
        {
            Console.WriteLine($"✖ {registerMessage}");
            Console.WriteLine();
            Console.WriteLine("Presioná ENTER para salir...");
            Console.ReadLine();
            return 1;
        }
        Console.WriteLine("✔ Servicio instalado.");
        Console.WriteLine();

        var alreadyPaired = DeviceCredentialsStore.Exists();
        if (alreadyPaired)
        {
            Console.WriteLine("Paso 2/3: Esta PC ya estaba emparejada antes.");
            Console.Write("¿Querés volver a emparejarla con un código nuevo? (s/N): ");
            var answer = Console.ReadLine();
            if (string.Equals(answer?.Trim(), "s", StringComparison.OrdinalIgnoreCase))
            {
                var pairExit = await RunPairingAsync();
                if (pairExit != 0) return pairExit;
            }
        }
        else
        {
            Console.WriteLine("Paso 2/3: Falta emparejar esta PC con tu cuenta.");
            Console.WriteLine("Abrí la app EncenderPCCompanion, agregá un dispositivo nuevo y");
            Console.WriteLine("anotá el código de 6 dígitos que te muestre.");
            Console.WriteLine();
            var pairExit = await RunPairingAsync();
            if (pairExit != 0) return pairExit;
        }

        Console.WriteLine("Paso 3/3: Iniciando el servicio...");
        ServiceInstaller.RestartService();
        if (ServiceInstaller.IsServiceRunning())
        {
            Console.WriteLine("✔ Listo. Esta PC ya está reportando su estado a EncenderPCCompanion.");
        }
        else
        {
            Console.WriteLine("El servicio quedó instalado pero no pudo confirmarse que arrancó.");
            Console.WriteLine("Revisá el Visor de eventos de Windows o reintentá desde 'Servicios'.");
        }

        Console.WriteLine();
        Console.WriteLine("Podés cerrar esta ventana. El agente sigue funcionando solo, incluso");
        Console.WriteLine("si reiniciás la PC.");
        Console.WriteLine();
        Console.WriteLine("Presioná ENTER para salir...");
        Console.ReadLine();
        return 0;
    }

    public static async Task<int> RunUninstallAsync()
    {
        Console.WriteLine("=== Desinstalar el agente de EncenderPCCompanion ===");
        Console.WriteLine();

        if (!ServiceInstaller.IsAdministrator())
        {
            Console.WriteLine("Se necesitan permisos de Administrador, relanzando...");
            var relaunched = ServiceInstaller.RelaunchElevated(new[] { "uninstall" });
            return relaunched ? 0 : 1;
        }

        ServiceInstaller.UninstallService();
        Console.WriteLine("✔ Servicio eliminado.");
        Console.WriteLine();
        Console.WriteLine("Presioná ENTER para salir...");
        Console.ReadLine();
        await Task.CompletedTask;
        return 0;
    }

    private static async Task<int> RunPairingAsync()
    {
        var pairingConfig = new ConfigurationBuilder()
            .SetBasePath(AppContext.BaseDirectory)
            .AddJsonFile("appsettings.json", optional: false)
            .Build();

        var firebaseSettings = new FirebaseSettings();
        pairingConfig.GetSection("Firebase").Bind(firebaseSettings);

        return await PairingFlow.RunAsync(firebaseSettings);
    }
}
