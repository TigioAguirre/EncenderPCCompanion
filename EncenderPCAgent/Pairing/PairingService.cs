using EncenderPCAgent.Config;
using EncenderPCAgent.Firebase;
using EncenderPCAgent.Installer;

namespace EncenderPCAgent.Pairing;

public sealed class PairingResult
{
    public bool Success { get; init; }
    public string? DeviceId { get; init; }
    public string? ErrorMessage { get; init; }

    /// <summary>true si, además de emparejar, se pudo reiniciar el servicio ya instalado.</summary>
    public bool ServiceRestarted { get; init; }

    /// <summary>true si el servicio todavía no estaba instalado (nada raro, es normal antes del paso de instalación).</summary>
    public bool ServiceNotInstalled { get; init; }
}

/// <summary>
/// Lógica de emparejamiento sin nada de UI (ni Console.WriteLine ni
/// MessageBox): valida el código de 6 dígitos, habla con las Cloud
/// Functions, guarda device.json y reinicia el servicio si corresponde.
/// La usan tanto <see cref="PairingFlow"/> (consola, "pair") como
/// <c>SetupWizardForm</c> (el wizard gráfico), para no duplicar la
/// llamada a Firebase en dos lugares.
/// </summary>
public static class PairingService
{
    public static async Task<PairingResult> PairAsync(string pairingCode, FirebaseSettings settings, CancellationToken ct = default)
    {
        var code = (pairingCode ?? "").Trim();
        if (code.Length != 6 || !code.All(char.IsDigit))
        {
            return new PairingResult { Success = false, ErrorMessage = "El código debe tener 6 números." };
        }

        using var http = new HttpClient();
        var pairingClient = new PairingClient(http, settings);
        var authClient = new FirebaseAuthClient(http, settings);

        try
        {
            var pairResult = await pairingClient.PairAsync(code, ct);
            var signIn = await authClient.SignInWithCustomTokenAsync(pairResult.CustomToken, ct);

            var credentials = new DeviceCredentials
            {
                DeviceId = pairResult.DeviceId,
                IdToken = signIn.IdToken,
                RefreshToken = signIn.RefreshToken,
                IdTokenExpiresAtUtc = DateTimeOffset.UtcNow.AddSeconds(signIn.ExpiresInSeconds)
            };

            DeviceCredentialsStore.Save(credentials);

            // Igual que en la consola: si el servicio ya estaba corriendo con
            // credenciales viejas (o recién instalado y todavía sin arrancar),
            // lo (re)arrancamos ahora mismo para que tome device.json sin
            // esperar a un reinicio de Windows.
            //
            // FIX: esto se hacía de forma síncrona en el mismo hilo que llama
            // a PairAsync. Desde la consola no importa, pero desde el wizard
            // gráfico ese hilo es el de la UI: "sc start"/"sc stop" con sus
            // esperas (hasta 45s) dejaban la ventana congelada ("no responde")
            // durante el primer emparejamiento, dando la sensación de que el
            // agente no arrancaba. Al moverlo a un hilo de threadpool con
            // Task.Run, la UI sigue respondiendo mientras se confirma.
            var serviceRestarted = false;
            var serviceNotInstalled = !ServiceInstaller.ServiceExists();
            if (!serviceNotInstalled && ServiceInstaller.IsAdministrator())
            {
                serviceRestarted = await Task.Run(() =>
                {
                    ServiceInstaller.RestartService();
                    return ServiceInstaller.IsServiceRunning();
                }, ct);
            }

            return new PairingResult
            {
                Success = true,
                DeviceId = credentials.DeviceId,
                ServiceRestarted = serviceRestarted,
                ServiceNotInstalled = serviceNotInstalled
            };
        }
        catch (Exception ex)
        {
            return new PairingResult { Success = false, ErrorMessage = ex.Message };
        }
    }
}
