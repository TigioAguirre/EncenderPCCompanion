using System.Text.Json;

namespace EncenderPCAgent.Config;

/// <summary>
/// Guarda/lee device.json en una carpeta que tanto el proceso interactivo
/// de emparejamiento (corrido como admin) como el servicio de Windows
/// (corrido como SYSTEM) pueden leer y escribir: ProgramData.
/// </summary>
public static class DeviceCredentialsStore
{
    private static readonly string Directory =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "EncenderPCAgent");

    private static readonly string FilePath = Path.Combine(Directory, "device.json");

    private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

    public static bool Exists() => File.Exists(FilePath);

    public static DeviceCredentials? Load()
    {
        if (!File.Exists(FilePath)) return null;
        var json = File.ReadAllText(FilePath);
        return JsonSerializer.Deserialize<DeviceCredentials>(json);
    }

    public static void Save(DeviceCredentials credentials)
    {
        if (!System.IO.Directory.Exists(Directory))
        {
            System.IO.Directory.CreateDirectory(Directory);
        }

        var json = JsonSerializer.Serialize(credentials, JsonOptions);
        File.WriteAllText(FilePath, json);
    }
}
