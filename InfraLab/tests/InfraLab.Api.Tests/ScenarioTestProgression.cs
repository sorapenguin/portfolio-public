using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using InfraLab.Contracts;
using InfraLab.Domain;

namespace InfraLab.Api.Tests;

internal static class ScenarioTestProgression
{
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };
    private static readonly IReadOnlyDictionary<string, Scenario> Scenarios = Load().ToDictionary(x => x.Id, StringComparer.Ordinal);

    public static async Task<PublicAttemptView> ReachDiagnoseAsync(HttpClient client, PublicAttemptView attempt)
    {
        var scenario = Scenarios[attempt.ScenarioId];
        for (var step = 0; attempt.Phase is (int)ScenarioPhase.Observe or (int)ScenarioPhase.Investigate; step++)
        {
            Assert.True(step < 100, $"INVESTIGATION_STALLED at step {step}.");
            var action = attempt.AvailableActions.FirstOrDefault(x => !attempt.ExecutedActions.Contains(x.Id, StringComparer.Ordinal));
            if (action is not null)
            {
                attempt = await SubmitAsync(client, attempt, "actions", new ActionRequest(action.Id, null, attempt.StateVersion, Guid.NewGuid().ToString("N")));
                continue;
            }

            var command = scenario.Version.Actions
                .SelectMany(actionDefinition => actionDefinition.Patterns.Select(pattern => (actionDefinition, pattern)))
                .FirstOrDefault(x => x.actionDefinition.CommandRevealsEvidenceIds.Any(evidenceId => !attempt.RevealedEvidence.Any(e => e.Id == evidenceId)));
            Assert.True(command.actionDefinition is not null, $"INVESTIGATION_STALLED at step {step}.");
            attempt = await SubmitAsync(client, attempt, "commands", new ActionRequest(null, command.pattern.ValidationInput, attempt.StateVersion, Guid.NewGuid().ToString("N")));
        }

        Assert.Equal((int)ScenarioPhase.Diagnose, attempt.Phase);
        return attempt;
    }

    private static async Task<PublicAttemptView> SubmitAsync(HttpClient client, PublicAttemptView attempt, string operation, object request)
    {
        var response = await client.PostAsJsonAsync($"/api/attempts/{attempt.Id}/{operation}", request);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var updated = await response.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.NotNull(updated);
        return updated!;
    }

    private static IReadOnlyList<Scenario> Load()
    {
        var root = new DirectoryInfo(Directory.GetCurrentDirectory());
        while (root is not null && !Directory.Exists(Path.Combine(root.FullName, "content"))) root = root.Parent;
        if (root is null) throw new DirectoryNotFoundException("Scenario content directory was not found.");
        return Directory.EnumerateFiles(Path.Combine(root.FullName, "content"), "*.json", SearchOption.AllDirectories)
            .Select(file => JsonSerializer.Deserialize<Scenario>(File.ReadAllText(file), JsonOptions) ?? throw new InvalidOperationException("Scenario JSON could not be read."))
            .ToArray();
    }
}
