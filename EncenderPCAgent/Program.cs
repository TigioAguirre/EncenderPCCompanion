using System.ServiceProcess;
using EncenderPCAgent;
using EncenderPCAgent.Config;
using EncenderPCAgent.Firebase;
using EncenderPCAgent.Installer;
using EncenderPCAgent.Pairing;
using EncenderPCAgent.Presence;
using EncenderPCAgent.UI;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Hosting.WindowsServices;
using Microsoft.Extensions.Options;

// --- Modo emparejamiento avanzado: "EncenderPCAgent.exe pair" ---
// Para quien prefiera la consola (o re-emparejar sin pasar por el wizard).
// Se corre a mano desde una consola normal (no como servicio).
if (args.Length > 0 && args[0].Equals("pair", StringComparison.OrdinalIgnoreCase))
{
    var pairingConfig = new ConfigurationBuilder()
        .SetBasePath(AppContext.BaseDirectory)
        .AddJsonFile("appsettings.json", optional: false)
        .Build();

    var firebaseSettings = new FirebaseSettings();
    pairingConfig.GetSection("Firebase").Bind(firebaseSettings);

    return await PairingFlow.RunAsync(firebaseSettings);
}

// --- Desinstalar: "EncenderPCAgent.exe uninstall" ---
if (args.Length > 0 && args[0].Equals("uninstall", StringComparison.OrdinalIgnoreCase))
{
    return await SetupWizard.RunUninstallAsync();
}

// --- Doble click sin argumentos, desde el Explorador de Windows ---
// WindowsServiceHelpers.IsWindowsService() es lo que distingue este caso
// (usuario interactivo) de cuando el propio Windows arranca el .exe como
// servicio (también sin argumentos).
if (args.Length == 0 && !WindowsServiceHelpers.IsWindowsService())
{
    return SetupWizardApp.Run();
}

// --- Modo normal: correr como servicio de Windows (o consola, para debug) ---

// FIX: Forzar a que el directorio de trabajo siempre sea la carpeta del .exe.
// Esto evita el Error 1022 al arrancar desde C:\Windows\System32.
Directory.SetCurrentDirectory(AppContext.BaseDirectory);

var builder = Host.CreateApplicationBuilder(args);

// Asegurar explícitamente que el Host busque appsettings.json en la carpeta correcta
builder.Environment.ContentRootPath = AppContext.BaseDirectory;

builder.Services.Configure<FirebaseSettings>(builder.Configuration.GetSection("Firebase"));
builder.Services.Configure<AgentSettings>(builder.Configuration.GetSection("Agent"));

builder.Services.AddSingleton(sp =>
    sp.GetRequiredService<IOptions<FirebaseSettings>>().Value);

builder.Services.AddHttpClient<FirebaseAuthClient>();
builder.Services.AddHttpClient<FirestoreHeartbeatClient>();
builder.Services.AddSingleton<PresenceReporter>();
builder.Services.AddHostedService<Worker>();

// Extiende el tiempo de gracia del Host de .NET para que Worker.StopAsync
// tenga margen de sobra para el POST de "offline" (cubre apagado/reinicio
// reales; el caso de Inicio rápido/suspender/hibernar lo cubre por separado
// EncenderPcWindowsService.OnPowerEvent más abajo).
builder.Services.Configure<HostOptions>(options =>
{
    options.ShutdownTimeout = TimeSpan.FromSeconds(15);
});

if (WindowsServiceHelpers.IsWindowsService())
{
    // Antes esto se resolvía con builder.Services.AddWindowsService(...),
    // que loguea al Visor de eventos y responde a Start/Stop del SCM, pero
    // es una clase interna sellada que NO permite escuchar eventos de
    // energía. Lo reemplazamos por EncenderPcWindowsService (ver esa
    // clase), que sí escucha Stop/Shutdown (apagado/reinicio reales) Y
    // el evento de suspensión (necesario para cuando "Apagar" es en
    // realidad una hibernación por Inicio rápido). Por eso, acá volvemos
    // a agregar manualmente el logueo al Visor de eventos que
    // AddWindowsService hacía por nosotros.
    builder.Logging.AddEventLog(settings =>
    {
        settings.SourceName = "EncenderPCAgent";
    });

    var serviceHost = builder.Build();
    ServiceBase.Run(new EncenderPcWindowsService(serviceHost));
    return 0;
}

// Modo consola (debug local, sin instalar como servicio).
var host = builder.Build();
host.Run();
return 0;
