using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using InfraLab.Contracts;
using InfraLab.Domain;
using InfraLab.Infrastructure;
using Microsoft.EntityFrameworkCore;

namespace InfraLab.Api.Tests;

[Collection("PostgreSQL")]
public sealed class ScenarioBranchCoverageTests(PostgresFixture fixture) : IAsyncLifetime
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public Task InitializeAsync() => fixture.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    [Fact]
    public async Task All_scenario_actions_and_required_evidence_are_reachable()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();

        foreach (var scenario in await ScenariosAsync(client))
        {
            var initial = await StartAsync(client, scenario.Id);
            Assert.NotEmpty(initial.AvailableActions);
            foreach (var action in initial.AvailableActions)
            {
                var attempt = await StartAsync(client, scenario.Id);
                var response = await client.PostAsJsonAsync($"/api/attempts/{attempt.Id}/actions",
                    new ActionRequest(action.Id, null, attempt.StateVersion, Guid.NewGuid().ToString("N")));
                Assert.Equal(HttpStatusCode.OK, response.StatusCode);
                var updated = await ReadAttemptAsync(response);
                Assert.Equal(attempt.StateVersion + 1, updated.StateVersion);
                Assert.Equal(updated.RevealedEvidence.Select(x => x.Id).Distinct(StringComparer.Ordinal).Count(), updated.RevealedEvidence.Length);
                Assert.Equal(updated.AvailableActions.Select(x => x.Id).Distinct(StringComparer.Ordinal).Count(), updated.AvailableActions.Length);
            }

            var diagnosed = await ReachDiagnoseAsync(client, scenario.Id);
            Assert.Equal((int)ScenarioPhase.Diagnose, diagnosed.Phase);
            Assert.Equal(diagnosed.RevealedEvidence.Select(x => x.Id).Distinct(StringComparer.Ordinal).Count(), diagnosed.RevealedEvidence.Length);
        }
    }

    [Fact]
    public async Task Every_diagnosis_candidate_can_complete_the_scenario()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();

        foreach (var scenario in await ScenariosAsync(client))
        {
            var template = await ReachDiagnoseAsync(client, scenario.Id);
            for (var index = 0; index < template.AvailableDiagnoses.Length; index++)
            {
                var attempt = await ReachDiagnoseAsync(client, scenario.Id);
                var beforeEvents = await EventCountAsync(attempt.Id);
                var response = await client.PostAsJsonAsync($"/api/attempts/{attempt.Id}/diagnosis",
                    new SubmitDiagnosisRequest(attempt.AvailableDiagnoses[index].Id, attempt.StateVersion, Guid.NewGuid().ToString("N")));
                Assert.Equal(HttpStatusCode.OK, response.StatusCode);
                var remediating = await ReadAttemptAsync(response);
                Assert.Equal((int)ScenarioPhase.Remediate, remediating.Phase);
                Assert.Equal(attempt.StateVersion + 1, remediating.StateVersion);
                Assert.Empty(remediating.AvailableDiagnoses);
                Assert.Equal(beforeEvents + 1, await EventCountAsync(attempt.Id));

                await CompleteFromRemediateAsync(client, remediating);
            }
        }
    }

    [Fact]
    public async Task Every_remediation_candidate_can_complete_the_scenario()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();

        foreach (var scenario in await ScenariosAsync(client))
        {
            var diagnosed = await ReachDiagnoseAsync(client, scenario.Id);
            var firstDiagnosis = diagnosed.AvailableDiagnoses[0];
            var template = await SubmitAsync(client, diagnosed, "diagnosis", new SubmitDiagnosisRequest(firstDiagnosis.Id, diagnosed.StateVersion, Guid.NewGuid().ToString("N")));
            for (var index = 0; index < template.AvailableRemediations.Length; index++)
            {
                diagnosed = await ReachDiagnoseAsync(client, scenario.Id);
                var remediating = await SubmitAsync(client, diagnosed, "diagnosis", new SubmitDiagnosisRequest(diagnosed.AvailableDiagnoses[0].Id, diagnosed.StateVersion, Guid.NewGuid().ToString("N")));
                var beforeEvents = await EventCountAsync(remediating.Id);
                var response = await client.PostAsJsonAsync($"/api/attempts/{remediating.Id}/remediation",
                    new SubmitRemediationRequest(remediating.AvailableRemediations[index].Id, remediating.StateVersion, Guid.NewGuid().ToString("N")));
                Assert.Equal(HttpStatusCode.OK, response.StatusCode);
                var verifying = await ReadAttemptAsync(response);
                Assert.Equal((int)ScenarioPhase.Verify, verifying.Phase);
                Assert.Equal(remediating.StateVersion + 1, verifying.StateVersion);
                Assert.Empty(verifying.AvailableRemediations);
                Assert.Equal(beforeEvents + 1, await EventCountAsync(remediating.Id));

                await CompleteFromVerifyAsync(client, verifying);
            }
        }
    }

    [Fact]
    public async Task Verification_selection_sets_complete_or_are_safely_rejected()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();

        foreach (var scenario in await ScenariosAsync(client))
        {
            var verificationTemplate = await ReachVerifyAsync(client, scenario.Id);
            var candidates = verificationTemplate.AvailableVerifications.Select(x => x.Id).ToArray();
            foreach (var selection in Selections(candidates))
            {
                var verifying = await ReachVerifyAsync(client, scenario.Id);
                await AssertVerificationOutcomeAsync(client, verifying, selection);
            }

            await AssertRejectedVerificationAsync(client, await ReachVerifyAsync(client, scenario.Id), []);
            await AssertRejectedVerificationAsync(client, await ReachVerifyAsync(client, scenario.Id), [candidates[0], candidates[0]]);
            await AssertRejectedVerificationAsync(client, await ReachVerifyAsync(client, scenario.Id), [candidates[0], "unknown-verification"]);
        }
    }

    [Fact]
    public async Task Completed_branch_scores_obey_invariants()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();

        foreach (var scenario in await ScenariosAsync(client))
        {
            var completed = await CompleteAsync(client, scenario.Id, 0, 0);
            await AssertCompletedInvariantsAsync(client, completed.Id);
        }
    }

    [Fact]
    public async Task Identical_scenario_choices_produce_deterministic_public_results()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();

        foreach (var scenario in await ScenariosAsync(client))
        {
            var first = await CompleteAsync(client, scenario.Id, 0, 0);
            var second = await CompleteAsync(client, scenario.Id, 0, 0);
            var firstResult = await client.GetFromJsonAsync<PublicAttemptResult>($"/api/attempts/{first.Id}/result");
            var secondResult = await client.GetFromJsonAsync<PublicAttemptResult>($"/api/attempts/{second.Id}/result");
            var firstReview = await client.GetFromJsonAsync<PublicAttemptReview>($"/api/attempts/{first.Id}/review");
            var secondReview = await client.GetFromJsonAsync<PublicAttemptReview>($"/api/attempts/{second.Id}/review");
            Assert.NotNull(firstResult); Assert.NotNull(secondResult); Assert.NotNull(firstReview); Assert.NotNull(secondReview);
            Assert.Equal(firstResult!.Score, secondResult!.Score);
            Assert.Equal(firstReview!.Diagnosis!.IsCorrect, secondReview!.Diagnosis!.IsCorrect);
            Assert.Equal(firstReview.Remediation!.IsCorrect, secondReview.Remediation!.IsCorrect);
            Assert.Equal(firstReview.Verification!.IsCorrect, secondReview.Verification!.IsCorrect);
            Assert.Equal(firstReview.Verification.SelectedLabels, secondReview.Verification.SelectedLabels);
            Assert.Equal(firstReview.Verification.ExpectedLabels, secondReview.Verification.ExpectedLabels);
        }
    }

    private async Task AssertVerificationOutcomeAsync(HttpClient client, PublicAttemptView verifying, IReadOnlyList<string> selection)
    {
        var beforeEvents = await EventCountAsync(verifying.Id);
        var response = await client.PostAsJsonAsync($"/api/attempts/{verifying.Id}/verification",
            new SubmitVerificationRequest(selection, verifying.StateVersion, Guid.NewGuid().ToString("N")));
        if (response.StatusCode == HttpStatusCode.OK)
        {
            var completed = await ReadAttemptAsync(response);
            Assert.Equal((int)ScenarioPhase.Review, completed.Phase);
            Assert.Equal((int)AttemptStatus.Completed, completed.Status);
            Assert.Equal(verifying.StateVersion + 1, completed.StateVersion);
            Assert.Empty(completed.AvailableVerifications);
            Assert.Equal(beforeEvents + 1, await EventCountAsync(verifying.Id));
            await AssertCompletedInvariantsAsync(client, verifying.Id);
            return;
        }

        Assert.Equal(HttpStatusCode.UnprocessableEntity, response.StatusCode);
        await AssertUnchangedAsync(client, verifying, beforeEvents);
    }

    private async Task AssertRejectedVerificationAsync(HttpClient client, PublicAttemptView verifying, IReadOnlyList<string> selection)
    {
        var beforeEvents = await EventCountAsync(verifying.Id);
        var response = await client.PostAsJsonAsync($"/api/attempts/{verifying.Id}/verification",
            new SubmitVerificationRequest(selection, verifying.StateVersion, Guid.NewGuid().ToString("N")));
        Assert.Equal(HttpStatusCode.UnprocessableEntity, response.StatusCode);
        await AssertUnchangedAsync(client, verifying, beforeEvents);
    }

    private async Task AssertUnchangedAsync(HttpClient client, PublicAttemptView before, int eventCount)
    {
        var after = await client.GetFromJsonAsync<PublicAttemptView>($"/api/attempts/{before.Id}");
        Assert.NotNull(after);
        Assert.Equal(before.Phase, after!.Phase);
        Assert.Equal(before.Status, after.Status);
        Assert.Equal(before.StateVersion, after.StateVersion);
        Assert.Equal(eventCount, await EventCountAsync(before.Id));
    }

    private async Task AssertCompletedInvariantsAsync(HttpClient client, Guid attemptId)
    {
        var before = await SnapshotAsync(attemptId);
        var result = await client.GetFromJsonAsync<PublicAttemptResult>($"/api/attempts/{attemptId}/result");
        var review = await client.GetFromJsonAsync<PublicAttemptReview>($"/api/attempts/{attemptId}/review");
        Assert.NotNull(result); Assert.NotNull(review);
        Assert.True(result!.Score.Diagnosis >= 0 && result.Score.Remediation >= 0 && result.Score.Verification >= 0 && result.Score.Investigation >= 0 && result.Score.Safety >= 0 && result.Score.Total >= 0);
        Assert.Equal(result.Score.Diagnosis + result.Score.Remediation + result.Score.Verification + result.Score.Investigation + result.Score.Safety, result.Score.Total);
        Assert.Equal(before.Score.Diagnosis, result.Score.Diagnosis);
        Assert.Equal(before.Score.Remediation, result.Score.Remediation);
        Assert.Equal(before.Score.Verification, result.Score.Verification);
        Assert.Equal(before.Score.Investigation, result.Score.Investigation);
        Assert.Equal(before.Score.Safety, result.Score.Safety);
        Assert.Equal(before.Score.Total, result.Score.Total);
        Assert.Equal(review!.Diagnosis!.IsCorrect, before.Score.Diagnosis > 0);
        Assert.Equal(review.Remediation!.IsCorrect, before.Score.Remediation > 0);
        Assert.Equal(review.Verification!.IsCorrect, before.Score.Verification > 0);
        var after = await SnapshotAsync(attemptId);
        Assert.Equal(before.Phase, after.Phase);
        Assert.Equal(before.Status, after.Status);
        Assert.Equal(before.StateVersion, after.StateVersion);
        Assert.Equal(before.EventCount, after.EventCount);
    }

    private async Task<PublicAttemptView> CompleteAsync(HttpClient client, string scenarioId, int diagnosisIndex, int remediationIndex)
    {
        var diagnosed = await ReachDiagnoseAsync(client, scenarioId);
        var remediating = await SubmitAsync(client, diagnosed, "diagnosis", new SubmitDiagnosisRequest(diagnosed.AvailableDiagnoses[diagnosisIndex].Id, diagnosed.StateVersion, Guid.NewGuid().ToString("N")));
        var verifying = await SubmitAsync(client, remediating, "remediation", new SubmitRemediationRequest(remediating.AvailableRemediations[remediationIndex].Id, remediating.StateVersion, Guid.NewGuid().ToString("N")));
        return await CompleteFromVerifyAsync(client, verifying);
    }

    private async Task<PublicAttemptView> CompleteFromRemediateAsync(HttpClient client, PublicAttemptView remediating)
    {
        var verifying = await SubmitAsync(client, remediating, "remediation", new SubmitRemediationRequest(remediating.AvailableRemediations[0].Id, remediating.StateVersion, Guid.NewGuid().ToString("N")));
        return await CompleteFromVerifyAsync(client, verifying);
    }

    private async Task<PublicAttemptView> CompleteFromVerifyAsync(HttpClient client, PublicAttemptView verifying)
    {
        var completed = await SubmitAsync(client, verifying, "verification", new SubmitVerificationRequest(verifying.AvailableVerifications.Select(x => x.Id).ToArray(), verifying.StateVersion, Guid.NewGuid().ToString("N")));
        Assert.Equal((int)ScenarioPhase.Review, completed.Phase);
        Assert.Equal((int)AttemptStatus.Completed, completed.Status);
        await AssertCompletedInvariantsAsync(client, completed.Id);
        return completed;
    }

    private static async Task<PublicAttemptView> ReachDiagnoseAsync(HttpClient client, string scenarioId)
    {
        var attempt = await StartAsync(client, scenarioId);
        return await ScenarioTestProgression.ReachDiagnoseAsync(client, attempt);
    }

    private static async Task<PublicAttemptView> ReachVerifyAsync(HttpClient client, string scenarioId)
    {
        var diagnosed = await ReachDiagnoseAsync(client, scenarioId);
        var remediating = await SubmitAsync(client, diagnosed, "diagnosis", new SubmitDiagnosisRequest(diagnosed.AvailableDiagnoses[0].Id, diagnosed.StateVersion, Guid.NewGuid().ToString("N")));
        return await SubmitAsync(client, remediating, "remediation", new SubmitRemediationRequest(remediating.AvailableRemediations[0].Id, remediating.StateVersion, Guid.NewGuid().ToString("N")));
    }

    private static async Task<PublicAttemptView> StartAsync(HttpClient client, string scenarioId)
    {
        var response = await client.PostAsync($"/api/scenarios/{scenarioId}/attempts", null);
        Assert.Equal(HttpStatusCode.Created, response.StatusCode);
        return await ReadAttemptAsync(response);
    }

    private static async Task<PublicAttemptView> SubmitAsync(HttpClient client, PublicAttemptView attempt, string operation, object request)
    {
        var response = await client.PostAsJsonAsync($"/api/attempts/{attempt.Id}/{operation}", request);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return await ReadAttemptAsync(response);
    }

    private static async Task<PublicAttemptView> ReadAttemptAsync(HttpResponseMessage response)
    {
        var attempt = await response.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.NotNull(attempt);
        return attempt!;
    }

    private async Task<int> EventCountAsync(Guid id)
    {
        await using var db = fixture.CreateContext();
        return await db.AttemptEvents.CountAsync(x => x.AttemptId == id);
    }

    private async Task<Snapshot> SnapshotAsync(Guid id)
    {
        await using var db = fixture.CreateContext();
        var entity = await db.Attempts.AsNoTracking().SingleAsync(x => x.Id == id);
        var score = JsonSerializer.Deserialize<ScoreBreakdown>(entity.ScoreJson!, JsonOptions);
        Assert.NotNull(score);
        var events = await db.AttemptEvents.CountAsync(x => x.AttemptId == id);
        return new(entity.CurrentPhase, entity.Status, entity.StateVersion, events, score!);
    }

    private static async Task<ScenarioListItem[]> ScenariosAsync(HttpClient client)
    {
        var scenarios = await client.GetFromJsonAsync<ScenarioListItem[]>("/api/scenarios");
        Assert.NotNull(scenarios);
        Assert.NotEmpty(scenarios!);
        return scenarios!;
    }

    private static IEnumerable<IReadOnlyList<string>> Selections(string[] candidates)
    {
        if (candidates.Length <= 8)
        {
            for (var mask = 1; mask < 1 << candidates.Length; mask++)
                yield return candidates.Where((_, index) => (mask & (1 << index)) != 0).ToArray();
            yield break;
        }

        yield return candidates;
        foreach (var candidate in candidates) yield return [candidate];
        foreach (var candidate in candidates) yield return candidates.Where(x => x != candidate).ToArray();
    }

    private sealed record Snapshot(ScenarioPhase Phase, AttemptStatus Status, int StateVersion, int EventCount, ScoreBreakdown Score);
}
