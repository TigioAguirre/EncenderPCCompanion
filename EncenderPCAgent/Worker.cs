using EncenderPCAgent.Config;
using EncenderPCAgent.Presence;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace EncenderPCAgent;

public sealed class Worker : BackgroundService
{
    private readonly ILogger<Worker> _logger;
    private readonly PresenceReporter _presence;
    private readonly AgentSettings _agentSettings;

    public Worker(
        ILogger<Worker> logger,
        PresenceReporter presence,
        IOptions<AgentSettings> agentSettings)
    {
        _logger = logger;
        _presence = presence;
        _agentSettings = agentSettings.Value;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        if (!_presence.TryLoadCredentials())
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
                await _presence.ReportAsync("online", stoppingToken);
                _logger.LogInformation("Heartbeat enviado.");
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
        // Best-effort: cubre apagado/reinicio REALES (o "net stop"), donde
        // Windows sí nos avisa. El caso de Inicio rápido / suspender /
        // hibernar (donde esto NUNCA se ejecuta) lo cubre por separado
        // EncenderPcWindowsService.OnPowerEvent, que llama a
        // PresenceReporter directamente sin pasar por acá.
        await _presence.TryReportAsync("offline", TimeSpan.FromSeconds(14), cancellationToken);
        await base.StopAsync(cancellationToken);
    }
}
