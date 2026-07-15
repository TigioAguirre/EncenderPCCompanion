using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using EncenderPCAgent.Config;

namespace EncenderPCAgent.Firebase;

/// <summary>
/// Escribe directamente contra la REST API de Firestore. Como las reglas
/// de seguridad (firestore.rules) solo dejan que este idToken toque los
/// campos "status" y "lastSeen" de SU documento, no hace falta ningún SDK:
/// un PATCH con updateMask alcanza.
/// </summary>
public sealed class FirestoreHeartbeatClient
{
    private readonly HttpClient _http;
    private readonly FirebaseSettings _settings;

    public FirestoreHeartbeatClient(HttpClient http, FirebaseSettings settings)
    {
        _http = http;
        _settings = settings;
    }

    public async Task SendHeartbeatAsync(string idToken, string deviceId, string status, CancellationToken ct)
    {
        var url =
            $"https://firestore.googleapis.com/v1/projects/{_settings.ProjectId}/databases/(default)/documents/devices/{deviceId}" +
            "?updateMask.fieldPaths=status&updateMask.fieldPaths=lastSeen";

        var nowIso = DateTimeOffset.UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ");

        var bodyJson = JsonSerializer.Serialize(new
        {
            fields = new
            {
                status = new { stringValue = status },
                lastSeen = new { timestampValue = nowIso }
            }
        });

        using var request = new HttpRequestMessage(HttpMethod.Patch, url)
        {
            Content = new StringContent(bodyJson, Encoding.UTF8, "application/json")
        };
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", idToken);

        using var response = await _http.SendAsync(request, ct);

        if (!response.IsSuccessStatusCode)
        {
            var error = await response.Content.ReadAsStringAsync(ct);
            throw new HttpRequestException(
                $"Firestore respondió {(int)response.StatusCode} {response.StatusCode} al mandar heartbeat: {error}");
        }
    }
}
