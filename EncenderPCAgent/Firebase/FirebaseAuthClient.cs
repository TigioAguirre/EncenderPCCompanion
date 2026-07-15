using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using EncenderPCAgent.Config;

namespace EncenderPCAgent.Firebase;

public sealed class SignInResult
{
    public string IdToken { get; set; } = "";
    public string RefreshToken { get; set; } = "";
    public int ExpiresInSeconds { get; set; }
}

/// <summary>
/// Habla con los endpoints públicos de Identity Toolkit / Secure Token
/// de Google. No usa ningún SDK de Firebase (no existe uno oficial para
/// .NET en escritorio) — son simples llamadas REST documentadas por Google.
/// </summary>
public sealed class FirebaseAuthClient
{
    private readonly HttpClient _http;
    private readonly FirebaseSettings _settings;

    public FirebaseAuthClient(HttpClient http, FirebaseSettings settings)
    {
        _http = http;
        _settings = settings;
    }

    public async Task<SignInResult> SignInWithCustomTokenAsync(string customToken, CancellationToken ct)
    {
        var url = $"https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key={_settings.ApiKey}";
        var response = await _http.PostAsJsonAsync(url, new
        {
            token = customToken,
            returnSecureToken = true
        }, ct);

        await EnsureSuccessAsync(response, ct);

        var body = await response.Content.ReadFromJsonAsync<SignInWithCustomTokenResponse>(cancellationToken: ct)
                   ?? throw new InvalidOperationException("Respuesta vacía de signInWithCustomToken.");

        return new SignInResult
        {
            IdToken = body.IdToken,
            RefreshToken = body.RefreshToken,
            ExpiresInSeconds = int.Parse(body.ExpiresIn)
        };
    }

    public async Task<SignInResult> RefreshIdTokenAsync(string refreshToken, CancellationToken ct)
    {
        var url = $"https://securetoken.googleapis.com/v1/token?key={_settings.ApiKey}";
        var form = new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["grant_type"] = "refresh_token",
            ["refresh_token"] = refreshToken
        });

        var response = await _http.PostAsync(url, form, ct);
        await EnsureSuccessAsync(response, ct);

        var body = await response.Content.ReadFromJsonAsync<RefreshTokenResponse>(cancellationToken: ct)
                   ?? throw new InvalidOperationException("Respuesta vacía de refresh token.");

        return new SignInResult
        {
            IdToken = body.IdToken,
            RefreshToken = body.RefreshToken,
            ExpiresInSeconds = int.Parse(body.ExpiresIn)
        };
    }

    private static async Task EnsureSuccessAsync(HttpResponseMessage response, CancellationToken ct)
    {
        if (response.IsSuccessStatusCode) return;

        var errorBody = await response.Content.ReadAsStringAsync(ct);
        throw new HttpRequestException(
            $"Firebase Auth respondió {(int)response.StatusCode} {response.StatusCode}: {errorBody}");
    }

    private sealed class SignInWithCustomTokenResponse
    {
        [JsonPropertyName("idToken")] public string IdToken { get; set; } = "";
        [JsonPropertyName("refreshToken")] public string RefreshToken { get; set; } = "";
        [JsonPropertyName("expiresIn")] public string ExpiresIn { get; set; } = "3600";
    }

    private sealed class RefreshTokenResponse
    {
        [JsonPropertyName("id_token")] public string IdToken { get; set; } = "";
        [JsonPropertyName("refresh_token")] public string RefreshToken { get; set; } = "";
        [JsonPropertyName("expires_in")] public string ExpiresIn { get; set; } = "3600";
    }
}
