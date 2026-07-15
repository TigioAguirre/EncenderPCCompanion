using EncenderPCAgent.Config;
using EncenderPCAgent.Firebase;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace EncenderPCAgent;

public sealed class Worker : BackgroundService
{
    private readonly ILogger<Worker> _logger;
    private readonly FirebaseAuthClient _authClient;
    private readonly FirestoreHeartbeatClient _heartbeatClient;
    private readonly AgentSettings _agentSettings;

    // Margen de seguridad para renovar el idToken antes de que expire de verdad.
    private static readonly TimeSpan TokenRefreshMargin = TimeSpan.FromMinutes(5);

    private DeviceCredentials? _credentials;

    public Worker(
        ILogger<Worker> logger,
        FirebaseAuthClient authClient,
        FirestoreHeartbeatClient heartbeatClient,
        IOptions<AgentSettings> agentSettings)
    {
        _logger = logger;
        _authClient = authClient;
        _heartbeatClient = heartbeatClient;
        _agentSettings = agentSettings.Value;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        _credentials = DeviceCredentialsStore.Load();

        if (_credentials is null || string.IsNullOrEmpty(_credentials.DeviceId))
        {
            _logger.LogError(
                "Esta PC todavía no está emparejada. Corré 'EncenderPCAgent.exe pair' desde una consola " +
                "antes de iniciar el servicio.");
            return;
        }

        var interval = TimeSpan.FromSeconds(Math.Max(10, _agentSettings.HeartbeatIntervalSeconds));

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await EnsureFreshTokenAsync(stoppingToken);
                await _heartbeatClient.SendHeartbeatAsync(_credentials.IdToken, _credentials.DeviceId, "online", stoppingToken);
                _logger.LogInformation("Heartbeat enviado ({DeviceId}).", _credentials.DeviceId);
            }
            catch (OperationCanceledException)
            {
                // El servicio se está deteniendo, no es un error real.
            }
            catch (Exception ex)
            {
                // No tiramos el servicio abajo por un heartbeat fallido puntual
                // (ej. corte de internet): reintentamos en el próximo ciclo.
                _logger.LogWarning(ex, "No se pudo mandar el heartbeat, se reintenta en {Interval}.", interval);
            }

            try
            {
                await Task.Delay(interval, stoppingToken);
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }
    }

    public override async Task StopAsync(CancellationToken cancellationToken)
    {
        // Best-effort: si Windows nos da tiempo (apagado/reinicio normal,
        // "net stop"), avisamos que la PC se está apagando. Si el corte es
        // abrupto (falla de energía, crash), esto no llega a ejecutarse y
        // ahí es la Cloud Function "checkOfflineDevices" la que marca
        // offline por falta de heartbeat.
        if (_credentials is not null)
        {
            try
            {
                using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
                await _heartbeatClient.SendHeartbeatAsync(_credentials.IdToken, _credentials.DeviceId, "offline", cts.Token);
                _logger.LogInformation("PC marcada como offline antes de detener el servicio.");
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "No se pudo avisar el apagado a tiempo, quedará offline por timeout igual.");
            }
        }

        await base.StopAsync(cancellationToken);
    }

    private async Task EnsureFreshTokenAsync(CancellationToken ct)
    {
        if (_credentials is null) return;

        if (DateTimeOffset.UtcNow < _credentials.IdTokenExpiresAtUtc - TokenRefreshMargin)
        {
            return; // el token actual todavía sirve
        }

        var refreshed = await _authClient.RefreshIdTokenAsync(_credentials.RefreshToken, ct);

        _credentials.IdToken = refreshed.IdToken;
        _credentials.RefreshToken = refreshed.RefreshToken;
        _credentials.IdTokenExpiresAtUtc = DateTimeOffset.UtcNow.AddSeconds(refreshed.ExpiresInSeconds);

        DeviceCredentialsStore.Save(_credentials);
        _logger.LogInformation("Token renovado, válido hasta {ExpiresAt} UTC.", _credentials.IdTokenExpiresAtUtc);
    }
}
