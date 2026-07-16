using EncenderPCAgent.Config;
using EncenderPCAgent.Presence;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using System.Net.NetworkInformation;

namespace EncenderPCAgent;

public sealed class Worker : BackgroundService
{
    private readonly ILogger<Worker> _logger;
    private readonly PresenceReporter _presence;
    private readonly AgentSettings _agentSettings;

    // Se "avisa" (TrySetResult) desde OnNetworkAvailabilityChanged para
    // cortar la espera del loop antes de tiempo. Ver comentario en
    // ExecuteAsync: existe para el arranque en frío (sin Inicio rápido),
    // donde el servicio puede arrancar (ahora con start=auto, ver
    // ServiceInstaller) una fracción de segundo antes de que el adaptador
    // de red tenga IP/ruta real, aunque el servicio Tcpip ya figure
    // "Running". Sin esto, ese primer heartbeat fallido recién se
    // reintentaría en el próximo ciclo (hasta 30s); con esto, se reintenta
    // apenas Windows avisa que la red volvió a estar disponible.
    private TaskCompletionSource<bool>? _wake;
    private readonly object _wakeLock = new();

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

        NetworkChange.NetworkAvailabilityChanged += OnNetworkAvailabilityChanged;
        NetworkChange.NetworkAddressChanged += OnNetworkAddressChanged;
        try
        {
            while (!stoppingToken.IsCancellationRequested)
            {
                var reportOk = true;
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
                    reportOk = false;
                    // No tiramos el servicio abajo por un heartbeat fallido puntual
                    // (ej. corte de internet): reintentamos en el próximo ciclo,
                    // o antes si Windows avisa que la red volvió (ver Wait...).
                    _logger.LogWarning(ex, "No se pudo mandar el heartbeat, se reintenta en {Interval} (o antes, si vuelve la red).", interval);
                }

                try
                {
                    // Si el heartbeat falló, esperamos como máximo `interval`,
                    // pero cortamos antes si llega un aviso de red disponible.
                    // Si salió bien, esperamos el intervalo normal sin más.
                    if (reportOk)
                    {
                        await Task.Delay(interval, stoppingToken);
                    }
                    else
                    {
                        await WaitForIntervalOrNetworkAsync(interval, stoppingToken);
                    }
                }
                catch (OperationCanceledException)
                {
                    break;
                }
            }
        }
        finally
        {
            NetworkChange.NetworkAvailabilityChanged -= OnNetworkAvailabilityChanged;
            NetworkChange.NetworkAddressChanged -= OnNetworkAddressChanged;
        }
    }

    private async Task WaitForIntervalOrNetworkAsync(TimeSpan interval, CancellationToken stoppingToken)
    {
        var tcs = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        lock (_wakeLock)
        {
            _wake = tcs;
        }

        try
        {
            var delayTask = Task.Delay(interval, stoppingToken);
            await Task.WhenAny(delayTask, tcs.Task);
        }
        finally
        {
            lock (_wakeLock)
            {
                if (ReferenceEquals(_wake, tcs))
                {
                    _wake = null;
                }
            }
        }

        stoppingToken.ThrowIfCancellationRequested();
    }

    private void OnNetworkAvailabilityChanged(object? sender, NetworkAvailabilityEventArgs e)
    {
        if (e.IsAvailable)
        {
            Wake();
        }
    }

    private void OnNetworkAddressChanged(object? sender, EventArgs e) => Wake();

    private void Wake()
    {
        lock (_wakeLock)
        {
            _wake?.TrySetResult(true);
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
