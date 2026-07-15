namespace EncenderPCAgent.Config;

/// <summary>
/// Datos públicos del proyecto Firebase, vienen de appsettings.json.
/// El ApiKey de Firebase Web NO es secreto: identifica al proyecto,
/// no da permisos por sí solo (eso lo controlan las Firestore rules).
/// </summary>
public sealed class FirebaseSettings
{
    public string ProjectId { get; set; } = "";
    public string ApiKey { get; set; } = "";
    public string FunctionsRegion { get; set; } = "us-central1";
}

public sealed class AgentSettings
{
    public int HeartbeatIntervalSeconds { get; set; } = 30;
}

/// <summary>
/// Credenciales de ESTA PC puntual, generadas durante el emparejamiento
/// (encenderpcagent.exe pair). Se guardan en
/// C:\ProgramData\EncenderPCAgent\device.json, fuera de la carpeta de
/// instalación, para que sobrevivan a una reinstalación del agente.
/// </summary>
public sealed class DeviceCredentials
{
    public string DeviceId { get; set; } = "";
    public string IdToken { get; set; } = "";
    public string RefreshToken { get; set; } = "";
    public DateTimeOffset IdTokenExpiresAtUtc { get; set; } = DateTimeOffset.MinValue;
}
