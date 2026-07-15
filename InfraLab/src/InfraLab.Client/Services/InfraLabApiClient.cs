using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using InfraLab.Client.Models;
using InfraLab.Contracts;

namespace InfraLab.Client.Services;

public sealed class InfraLabApiClient(HttpClient http)
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public async Task<IReadOnlyList<ScenarioListItem>> GetScenariosAsync(CancellationToken ct) =>
        await http.GetFromJsonAsync<List<ScenarioListItem>>("api/scenarios", JsonOptions, ct) ?? [];

    public async Task<PublicAttemptView> StartAttemptAsync(string scenarioId, CancellationToken ct)
    {
        var response = await http.PostAsync($"api/scenarios/{Uri.EscapeDataString(scenarioId)}/attempts", null, ct);
        return await ReadAsync<PublicAttemptView>(response, ct);
    }

    public async Task<PublicAttemptView> GetAttemptAsync(Guid id, CancellationToken ct)
    {
        var response = await http.GetAsync($"api/attempts/{id}", ct);
        return await ReadAsync<PublicAttemptView>(response, ct);
    }

    public async Task<PublicAttemptResult> GetAttemptResultAsync(Guid attemptId, CancellationToken ct = default)
    {
        var response = await http.GetAsync($"api/attempts/{attemptId}/result", ct);
        return await ReadAsync<PublicAttemptResult>(response, ct);
    }

    public async Task<PublicAttemptReview> GetAttemptReviewAsync(Guid attemptId, CancellationToken ct = default)
    {
        var response = await http.GetAsync($"api/attempts/{attemptId}/review", ct);
        return await ReadAsync<PublicAttemptReview>(response, ct);
    }

    public async Task<PublicAttemptView> SubmitActionAsync(Guid attemptId, ActionRequest request, CancellationToken ct = default)
    {
        var response = await http.PostAsJsonAsync($"api/attempts/{attemptId}/actions", request, JsonOptions, ct);
        return await ReadAsync<PublicAttemptView>(response, ct);
    }

    public async Task<PublicAttemptView> SubmitCommandAsync(Guid attemptId, ActionRequest request, CancellationToken ct = default)
    {
        var response = await http.PostAsJsonAsync($"api/attempts/{attemptId}/commands", request, JsonOptions, ct);
        return await ReadAsync<PublicAttemptView>(response, ct);
    }

    public async Task<PublicAttemptView> SubmitDiagnosisAsync(Guid attemptId, SubmitDiagnosisRequest request, CancellationToken ct = default)
    {
        var response = await http.PostAsJsonAsync($"api/attempts/{attemptId}/diagnosis", request, JsonOptions, ct);
        return await ReadAsync<PublicAttemptView>(response, ct);
    }

    public async Task<PublicAttemptView> SubmitRemediationAsync(Guid attemptId, SubmitRemediationRequest request, CancellationToken ct = default)
    {
        var response = await http.PostAsJsonAsync($"api/attempts/{attemptId}/remediation", request, JsonOptions, ct);
        return await ReadAsync<PublicAttemptView>(response, ct);
    }

    public async Task<PublicAttemptView> SubmitVerificationAsync(Guid attemptId, SubmitVerificationRequest request, CancellationToken ct = default)
    {
        var response = await http.PostAsJsonAsync($"api/attempts/{attemptId}/verification", request, JsonOptions, ct);
        return await ReadAsync<PublicAttemptView>(response, ct);
    }

    private static async Task<T> ReadAsync<T>(HttpResponseMessage response, CancellationToken ct)
    {
        if (response.StatusCode == HttpStatusCode.NotFound) throw new ApiNotFoundException();
        if (!response.IsSuccessStatusCode) throw new ApiRequestException(response.StatusCode);
        try
        {
            return await response.Content.ReadFromJsonAsync<T>(cancellationToken: ct) ?? throw new ApiRequestException(HttpStatusCode.BadGateway);
        }
        catch (JsonException)
        {
            throw new ApiRequestException(HttpStatusCode.BadGateway);
        }
    }
}

public sealed class ApiNotFoundException : Exception;
public sealed class ApiRequestException(HttpStatusCode statusCode) : Exception
{
    public HttpStatusCode StatusCode { get; } = statusCode;
}
