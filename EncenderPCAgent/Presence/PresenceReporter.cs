using EncenderPCAgent.Config;
using EncenderPCAgent.Firebase;
using Microsoft.Extensions.Logging;

namespace EncenderPCAgent.Presence;

/// <summary>
/// Centraliza el envío de "online"/"offline" a Firestore, incluyendo la
/// renovación del idToken. La usan tanto <see cref="EncenderPCAgent.Worker"/>
/// (loop normal + Stop/Shutdown reales) como
/// <see cref="EncenderPCAgent.EncenderPcWindowsService"/> (evento de
/// suspensión/hibernación: dormir, hibernar, o "Apagar" con Inicio rápido
/// activado — casos en los que Windows NUNCA llega a mandar Stop/Shutdown
/// al servicio, así que es la única oportunidad de avisar "offline" antes
/// de que el proceso quede congelado).
///
/// Un solo lugar para la lógica de token, y un <see cref="SemaphoreSlim"/>
/// para que un heartbeat normal y un aviso de suspensión no se pisen si
/// caen al mismo tiempo.
/// </summary>
public sealed class PresenceReporter
{
    private static readonly TimeSpan TokenRefreshMargin = TimeSpan.FromMinutes(5);

    private readonly ILogger<PresenceReporter> _logger;
    private readonly FirebaseAuthClient _authClient;
    private readonly FirestoreHeartbeatClient _heartbeatClient;
    private readonly SemaphoreSlim _lock = new(1, 1);

    private DeviceCredentials? _credentials;

    public PresenceReporter(
        ILogger<PresenceReporter> logger,
        FirebaseAuthClient authClient,
        FirestoreHeartbeatClient heartbeatClient)
    {
        _logger = logger;
        _authClient = authClient;
        _heartbeatClient = heartbeatClient;
    }

    /// <summary>Carga device.json. Devuelve false si esta PC todavía no está emparejada.</summary>
    public bool TryLoadCredentials()
    {
        _credentials = DeviceCredentialsStore.Load();
        return _credentials is not null && !string.IsNullOrEmpty(_credentials.DeviceId);
    }

    public bool HasCredentials => _credentials is not null;

    /// <summary>Manda el estado y deja que la excepción se propague (para el loop normal, que ya maneja sus propios reintentos).</summary>
    public async Task ReportAsync(string status, CancellationToken ct)
    {
        await _lock.WaitAsync(ct);
        try
        {
            if (_credentials is null) return;
            await EnsureFreshTokenAsync(ct);
            await _heartbeatClient.SendHeartbeatAsync(_credentials.IdToken, _credentials.DeviceId, status, ct);
        }
        finally
        {
            _lock.Release();
        }
    }

    /// <summary>
    /// Best-effort con timeout propio: para llamarse desde manejadores que
    /// no pueden dejar pasar una excepción hacia arriba (StopAsync,
    /// OnPowerEvent). Nunca lanza.
    /// </summary>
    public async Task TryReportAsync(string status, TimeSpan timeout, CancellationToken externalCt = default)
    {
        if (_credentials is null) return;
        try
        {
            using var cts = CancellationTokenSource.CreateLinkedTokenSource(externalCt);
            cts.CancelAfter(timeout);
            await ReportAsync(status, cts.Token);
            _logger.LogInformation("PC marcada como '{Status}'.", status);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "No se pudo avisar '{Status}' a tiempo.", status);
        }
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
