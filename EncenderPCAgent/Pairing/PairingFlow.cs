using EncenderPCAgent.Config;
using EncenderPCAgent.Firebase;
using EncenderPCAgent.Installer;

namespace EncenderPCAgent.Pairing;

/// <summary>
/// Se ejecuta con "EncenderPCAgent.exe pair". Es interactivo a propósito:
/// el servicio de Windows en sí no tiene consola, así que emparejar la PC
/// es un paso manual y único que hace el usuario después de instalar.
/// </summary>
public static class PairingFlow
{
    public static async Task<int> RunAsync(FirebaseSettings settings)
    {
        Console.WriteLine("=== Emparejar esta PC con EncenderPCCompanion ===");
        Console.WriteLine();

        if (DeviceCredentialsStore.Exists())
        {
            Console.Write("Esta PC ya está emparejada. ¿Querés volver a emparejarla igual? (s/N): ");
            var answer = Console.ReadLine();
            if (!string.Equals(answer?.Trim(), "s", StringComparison.OrdinalIgnoreCase))
            {
                Console.WriteLine("Cancelado, no se cambió nada.");
                return 0;
            }
        }

        Console.Write("Ingresá el código de 6 dígitos que te muestra la app: ");
        var code = (Console.ReadLine() ?? "").Trim();

        Console.WriteLine("Validando código...");
        var result = await PairingService.PairAsync(code, settings);

        if (!result.Success)
        {
            Console.WriteLine();
            Console.WriteLine($"✖ Falló el emparejamiento: {result.ErrorMessage}");
            return 1;
        }

        Console.WriteLine();
        Console.WriteLine("✔ Emparejado correctamente.");
        Console.WriteLine($"  deviceId: {result.DeviceId}");
        Console.WriteLine();

        // Clave para el bug de "queda offline al re-vincular": si el
        // servicio ya estaba corriendo (con credenciales viejas en
        // memoria), PairingService ya lo reinició para que relea
        // device.json ya, en vez de esperar a que alguien reinicie Windows.
        if (result.ServiceNotInstalled)
        {
            Console.WriteLine("Todavía no instalaste el servicio en esta PC.");
            Console.WriteLine("Ejecutá el instalador (doble click en EncenderPCAgent.exe) para instalarlo y arrancarlo.");
        }
        else if (result.ServiceRestarted)
        {
            Console.WriteLine("✔ Servicio reiniciado y corriendo con las credenciales nuevas.");
        }
        else
        {
            Console.WriteLine("Para que el cambio tenga efecto ya mismo, corré esto como Administrador:");
            Console.WriteLine("  net stop EncenderPCAgent && net start EncenderPCAgent");
        }

        return 0;
    }
}
