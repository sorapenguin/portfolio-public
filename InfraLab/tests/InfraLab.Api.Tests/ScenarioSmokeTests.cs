using System.Net;
using System.Net.Http.Json;
using InfraLab.Contracts;
using InfraLab.Domain;

namespace InfraLab.Api.Tests;

[Collection("PostgreSQL")]
public sealed class ScenarioSmokeTests(PostgresFixture fixture) : IAsyncLifetime
{
    public Task InitializeAsync() => fixture.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    [Fact]
    public async Task All_scenarios_can_reach_completed_state()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var scenarios = await client.GetFromJsonAsync<ScenarioListItem[]>("/api/scenarios");
        Assert.NotNull(scenarios);
        Assert.NotEmpty(scenarios!);

        foreach (var scenario in scenarios)
        {
            var start = await client.PostAsync($"/api/scenarios/{scenario.Id}/attempts", null);
            Assert.Equal(HttpStatusCode.Created, start.StatusCode);
            var attempt = await start.Content.ReadFromJsonAsync<PublicAttemptView>();
            Assert.NotNull(attempt);

            attempt = await ScenarioTestProgression.ReachDiagnoseAsync(client, attempt!);

            Assert.Equal((int)ScenarioPhase.Diagnose, attempt!.Phase);
            var diagnosis = Assert.Single(attempt.AvailableDiagnoses.Take(1));
            attempt = await SubmitAsync(client, attempt, "diagnosis", new SubmitDiagnosisRequest(diagnosis.Id, attempt.StateVersion, Guid.NewGuid().ToString("N")));
            Assert.Equal((int)ScenarioPhase.Remediate, attempt.Phase);

            var remediation = Assert.Single(attempt.AvailableRemediations.Take(1));
            attempt = await SubmitAsync(client, attempt, "remediation", new SubmitRemediationRequest(remediation.Id, attempt.StateVersion, Guid.NewGuid().ToString("N")));
            Assert.Equal((int)ScenarioPhase.Verify, attempt.Phase);

            var verificationIds = attempt.AvailableVerifications.Select(x => x.Id).ToArray();
            Assert.NotEmpty(verificationIds);
            attempt = await SubmitAsync(client, attempt, "verification", new SubmitVerificationRequest(verificationIds, attempt.StateVersion, Guid.NewGuid().ToString("N")));

            Assert.Equal((int)ScenarioPhase.Review, attempt.Phase);
            Assert.Equal((int)AttemptStatus.Completed, attempt.Status);
            Assert.True(attempt.StateVersion > 0, $"{scenario.Id}: COMPLETED_NOT_REACHED.");

            var result = await client.GetFromJsonAsync<PublicAttemptResult>($"/api/attempts/{attempt.Id}/result");
            var review = await client.GetFromJsonAsync<PublicAttemptReview>($"/api/attempts/{attempt.Id}/review");
            Assert.NotNull(result);
            Assert.NotNull(review);
            Assert.Equal(attempt.Id, result!.AttemptId);
            Assert.Equal(attempt.Id, review!.AttemptId);
            Assert.NotNull(review.Verification);
        }
    }

    private static async Task<PublicAttemptView> SubmitAsync(HttpClient client, PublicAttemptView attempt, string operation, object request)
    {
        var response = await client.PostAsJsonAsync($"/api/attempts/{attempt.Id}/{operation}", request);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var updated = await response.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.NotNull(updated);
        return updated!;
    }
}
