using System.ServiceProcess;
using EncenderPCAgent.Presence;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace EncenderPCAgent;

/// <summary>
/// Reemplaza a WindowsServiceLifetime (la que arma
/// <c>builder.Services.AddWindowsService(...)</c>): esa clase es interna y
/// sellada, y solo escucha Start/Stop/Shutdown. Acá necesitamos además
/// <see cref="OnPowerEvent"/>.
///
/// Por qué: con "Inicio rápido" activado (default en Windows 10/11),
/// "Apagar" no hace un apagado real de la sesión donde vive el servicio —
/// Windows la hiberna tal cual está. En ese caso el servicio NUNCA recibe
/// Stop ni Shutdown (por eso ningún timeout ahí alcanza): lo único que
/// recibe es el mismo evento de energía que se dispara al dormir/hibernar
/// a mano (SERVICE_CONTROL_POWEREVENT, PBT_APMSUSPEND). Escuchando ese
/// evento también, avisamos "offline" sin importar si Inicio rápido está
/// activado o no — ni depender de que el usuario cambie esa configuración.
///
/// Como bonus, al volver de suspender/hibernar avisamos "online" al
/// instante en vez de esperar hasta 30s al próximo heartbeat del Worker.
/// </summary>
public sealed class EncenderPcWindowsService : ServiceBase
{
    private readonly IHost _host;
    private readonly ILogger<EncenderPcWindowsService> _logger;
    private readonly PresenceReporter _presence;

    public EncenderPcWindowsService(IHost host)
    {
        _host = host;
        _logger = host.Services.GetRequiredService<ILogger<EncenderPcWindowsService>>();
        _presence = host.Services.GetRequiredService<PresenceReporter>();

        ServiceName = "EncenderPCAgent";
        CanStop = true;
        CanShutdown = true;
        CanHandlePowerEvent = true;
        AutoLog = false;
    }

    protected override void OnStart(string[] args)
    {
        // Start() es la versión sincrónica de StartAsync(): dispara
        // Worker.ExecuteAsync en segundo plano y vuelve enseguida (el SCM
        // espera que OnStart retorne rápido).
        _host.Start();
    }

    protected override void OnStop()
    {
        // Cubre apagado/reinicio reales y "net stop": el Host llama a
        // Worker.StopAsync (que ya manda "offline" con margen de 14s).
        _host.StopAsync(TimeSpan.FromSeconds(15)).GetAwaiter().GetResult();
    }

    protected override void OnShutdown()
    {
        OnStop();
    }

    protected override bool OnPowerEvent(PowerBroadcastStatus powerStatus)
    {
        // A diferencia de OnStop (que tiene hasta WaitToKillServiceTimeout
        // para responder), acá Windows espera una respuesta rápida antes
        // de completar la suspensión — por eso el timeout es corto (5s) en
        // vez de los 14s que usa el Stop normal.
        if (powerStatus == PowerBroadcastStatus.Suspend)
        {
            _presence.TryReportAsync("offline", TimeSpan.FromSeconds(5)).GetAwaiter().GetResult();
        }
        else if (powerStatus is PowerBroadcastStatus.ResumeSuspend
                              or PowerBroadcastStatus.ResumeCritical
                              or PowerBroadcastStatus.ResumeAutomatic)
        {
            _presence.TryReportAsync("online", TimeSpan.FromSeconds(5)).GetAwaiter().GetResult();
        }

        return true;
    }
}
