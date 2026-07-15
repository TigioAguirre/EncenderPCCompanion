using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using EncenderPCAgent.Config;

namespace EncenderPCAgent.Firebase;

public sealed class PairDeviceResult
{
    public string CustomToken { get; set; } = "";
    public string DeviceId { get; set; } = "";
}

/// <summary>
/// Las Cloud Functions "onCall" (como pairDevice) no son un endpoint REST
/// cualquiera: siguen el protocolo "callable" de Firebase, que envuelve
/// el body en {"data": ...} y la respuesta en {"result": ...}. Esta clase
/// reproduce ese protocolo a mano, sin necesitar el SDK cliente de Firebase
/// (que no existe para .NET de escritorio).
/// </summary>
public sealed class PairingClient
{
    private readonly HttpClient _http;
    private readonly FirebaseSettings _settings;

    public PairingClient(HttpClient http, FirebaseSettings settings)
    {
        _http = http;
        _settings = settings;
    }

    public async Task<PairDeviceResult> PairAsync(string pairingCode, CancellationToken ct)
    {
        var url = $"https://{_settings.FunctionsRegion}-{_settings.ProjectId}.cloudfunctions.net/pairDevice";

        var response = await _http.PostAsJsonAsync(url, new
        {
            data = new { pairingCode }
        }, ct);

        var raw = await response.Content.ReadAsStringAsync(ct);

        if (!response.IsSuccessStatusCode)
        {
            // Las callable functions devuelven el mensaje de error legible
            // en error.message (por ejemplo "Ese código ya fue usado.").
            var friendly = TryExtractErrorMessage(raw) ?? raw;
            throw new InvalidOperationException($"No se pudo emparejar: {friendly}");
        }

        var parsed = JsonSerializer.Deserialize<CallableEnvelope>(raw)
                     ?? throw new InvalidOperationException("Respuesta vacía de pairDevice.");

        return new PairDeviceResult
        {
            CustomToken = parsed.Result.CustomToken,
            DeviceId = parsed.Result.DeviceId
        };
    }

    private static string? TryExtractErrorMessage(string raw)
    {
        try
        {
            using var doc = JsonDocument.Parse(raw);
            if (doc.RootElement.TryGetProperty("error", out var error) &&
                error.TryGetProperty("message", out var message))
            {
                return message.GetString();
            }
        }
        catch (JsonException)
        {
            // No vino en el formato esperado, devolvemos null y usamos el raw tal cual.
        }
        return null;
    }

    private sealed class CallableEnvelope
    {
        [JsonPropertyName("result")] public CallableResult Result { get; set; } = new();
    }

    private sealed class CallableResult
    {
        [JsonPropertyName("customToken")] public string CustomToken { get; set; } = "";
        [JsonPropertyName("deviceId")] public string DeviceId { get; set; } = "";
    }
}
