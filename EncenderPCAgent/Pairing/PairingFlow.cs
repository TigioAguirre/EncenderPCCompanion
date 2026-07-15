using EncenderPCAgent.Config;
using EncenderPCAgent.Firebase;

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

        if (code.Length != 6 || !code.All(char.IsDigit))
        {
            Console.WriteLine("Ese código no tiene el formato esperado (6 números). Cancelado.");
            return 1;
        }

        using var http = new HttpClient();
        var pairingClient = new PairingClient(http, settings);
        var authClient = new FirebaseAuthClient(http, settings);

        try
        {
            Console.WriteLine("Validando código...");
            var pairResult = await pairingClient.PairAsync(code, CancellationToken.None);

            Console.WriteLine("Código válido. Iniciando sesión de la PC...");
            var signIn = await authClient.SignInWithCustomTokenAsync(pairResult.CustomToken, CancellationToken.None);

            var credentials = new DeviceCredentials
            {
                DeviceId = pairResult.DeviceId,
                IdToken = signIn.IdToken,
                RefreshToken = signIn.RefreshToken,
                IdTokenExpiresAtUtc = DateTimeOffset.UtcNow.AddSeconds(signIn.ExpiresInSeconds)
            };

            DeviceCredentialsStore.Save(credentials);

            Console.WriteLine();
            Console.WriteLine("✔ Emparejado correctamente.");
            Console.WriteLine($"  deviceId: {credentials.DeviceId}");
            Console.WriteLine();
            Console.WriteLine("Ahora podés iniciar el servicio (si no arranca solo):");
            Console.WriteLine("  net start EncenderPCAgent");
            return 0;
        }
        catch (Exception ex)
        {
            Console.WriteLine();
            Console.WriteLine($"✖ Falló el emparejamiento: {ex.Message}");
            return 1;
        }
    }
}
