using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using InfraLab.Contracts;
using InfraLab.Domain;

namespace InfraLab.Api.Tests;

[Collection("PostgreSQL")]
public sealed class CommandRequiredScenarioTests(PostgresFixture fixture) : IAsyncLifetime
{
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };

    public Task InitializeAsync() => fixture.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    [Fact]
    public async Task Command_required_scenario_does_not_reach_diagnose_with_actions_only()
    {
        var scenario = CommandRequiredScenario();
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var attempt = await StartAsync(client, scenario.Id);

        while (attempt.Phase is (int)ScenarioPhase.Observe or (int)ScenarioPhase.Investigate)
        {
            var action = attempt.AvailableActions.FirstOrDefault(x => !attempt.ExecutedActions.Contains(x.Id, StringComparer.Ordinal));
            if (action is null) break;
            attempt = await SubmitAsync(client, attempt, "actions", new ActionRequest(action.Id, null, attempt.StateVersion, Guid.NewGuid().ToString("N")));
        }

        Assert.NotEqual((int)ScenarioPhase.Diagnose, attempt.Phase);
        var commandOnlyRequired = CommandOnlyRequiredEvidence(scenario);
        Assert.DoesNotContain(attempt.RevealedEvidence, evidence => commandOnlyRequired.Contains(evidence.Id));
    }

    [Fact]
    public async Task Command_required_evidence_allows_diagnose_progression()
    {
        var scenario = CommandRequiredScenario();
        var commandAction = scenario.Version.Actions.First(action => action.CommandRevealsEvidenceIds.Any(CommandOnlyRequiredEvidence(scenario).Contains));
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var attempt = await StartAsync(client, scenario.Id);

        while (attempt.Phase is (int)ScenarioPhase.Observe or (int)ScenarioPhase.Investigate)
        {
            var action = attempt.AvailableActions.FirstOrDefault(x => !attempt.ExecutedActions.Contains(x.Id, StringComparer.Ordinal));
            if (action is null) break;
            attempt = await SubmitAsync(client, attempt, "actions", new ActionRequest(action.Id, null, attempt.StateVersion, Guid.NewGuid().ToString("N")));
        }

        var before = attempt.StateVersion;
        attempt = await SubmitAsync(client, attempt, "commands", new ActionRequest(null, commandAction.Patterns[0].ValidationInput, attempt.StateVersion, Guid.NewGuid().ToString("N")));
        Assert.Equal(before + 1, attempt.StateVersion);
        Assert.Equal((int)ScenarioPhase.Diagnose, attempt.Phase);
        Assert.Equal(attempt.RevealedEvidence.Length, attempt.RevealedEvidence.Select(x => x.Id).Distinct(StringComparer.Ordinal).Count());
        Assert.Contains(attempt.RevealedEvidence, evidence => commandAction.CommandRevealsEvidenceIds.Contains(evidence.Id));
    }

    [Fact]
    public async Task At_least_one_scenario_requires_command_evidence()
    {
        var scenario = CommandRequiredScenario();
        Assert.NotEmpty(CommandOnlyRequiredEvidence(scenario));
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var attempt = await StartAsync(client, scenario.Id);
        var diagnosed = await ScenarioTestProgression.ReachDiagnoseAsync(client, attempt);
        Assert.Equal((int)ScenarioPhase.Diagnose, diagnosed.Phase);
    }

    [Fact]
    public void Command_only_required_evidence_is_not_exposed_by_actions()
    {
        var scenario = CommandRequiredScenario();
        var commandOnlyRequired = CommandOnlyRequiredEvidence(scenario);
        Assert.DoesNotContain(scenario.Version.Actions.SelectMany(x => x.RevealsEvidenceIds), commandOnlyRequired.Contains);
        Assert.NotEmpty(scenario.Version.Actions.SelectMany(x => x.CommandRevealsEvidenceIds).Intersect(commandOnlyRequired));
    }

    private static IReadOnlySet<string> CommandOnlyRequiredEvidence(Scenario scenario) =>
        scenario.Version.Private.RequiredEvidenceIds
            .Where(required => scenario.Version.Actions.All(action => !action.RevealsEvidenceIds.Contains(required)))
            .Where(required => scenario.Version.Actions.Any(action => action.CommandRevealsEvidenceIds.Contains(required)))
            .ToHashSet(StringComparer.Ordinal);

    private static Scenario CommandRequiredScenario() => LoadScenarios().Single(scenario => CommandOnlyRequiredEvidence(scenario).Count > 0);

    private static async Task<PublicAttemptView> StartAsync(HttpClient client, string scenarioId)
    {
        var response = await client.PostAsync($"/api/scenarios/{scenarioId}/attempts", null);
        Assert.Equal(HttpStatusCode.Created, response.StatusCode);
        var attempt = await response.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.NotNull(attempt);
        return attempt!;
    }

    private static async Task<PublicAttemptView> SubmitAsync(HttpClient client, PublicAttemptView attempt, string operation, object request)
    {
        var response = await client.PostAsJsonAsync($"/api/attempts/{attempt.Id}/{operation}", request);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var updated = await response.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.NotNull(updated);
        return updated!;
    }

    private static IReadOnlyList<Scenario> LoadScenarios()
    {
        var root = new DirectoryInfo(Directory.GetCurrentDirectory());
        while (root is not null && !Directory.Exists(Path.Combine(root.FullName, "content"))) root = root.Parent;
        if (root is null) throw new DirectoryNotFoundException("Scenario content directory was not found.");
        return Directory.EnumerateFiles(Path.Combine(root.FullName, "content"), "*.json", SearchOption.AllDirectories)
            .Select(file => JsonSerializer.Deserialize<Scenario>(File.ReadAllText(file), JsonOptions) ?? throw new InvalidOperationException("Scenario JSON could not be read."))
            .ToArray();
    }
}
