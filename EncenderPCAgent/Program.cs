using EncenderPCAgent;
using EncenderPCAgent.Config;
using EncenderPCAgent.Firebase;
using EncenderPCAgent.Pairing;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Options;

// --- Modo emparejamiento: "EncenderPCAgent.exe pair" ---
// Se corre a mano, una sola vez, desde una consola normal (no como servicio).
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

// --- Modo normal: correr como servicio de Windows (o consola, para debug) ---
var builder = Host.CreateApplicationBuilder(args);

builder.Services.Configure<FirebaseSettings>(builder.Configuration.GetSection("Firebase"));
builder.Services.Configure<AgentSettings>(builder.Configuration.GetSection("Agent"));

builder.Services.AddSingleton(sp =>
    sp.GetRequiredService<IOptions<FirebaseSettings>>().Value);

builder.Services.AddHttpClient<FirebaseAuthClient>();
builder.Services.AddHttpClient<FirestoreHeartbeatClient>();
builder.Services.AddHostedService<Worker>();

// Hace que, cuando se instala como servicio de Windows, use el ciclo de
// vida correcto (responde a Start/Stop del SCM) y loguee al Visor de eventos.
builder.Services.AddWindowsService(options =>
{
    options.ServiceName = "EncenderPCAgent";
});

var host = builder.Build();
host.Run();
return 0;
