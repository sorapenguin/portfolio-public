using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using InfraLab.Application;
using InfraLab.Contracts;
using InfraLab.Domain;
using Microsoft.EntityFrameworkCore;

namespace InfraLab.Api.Tests;

[Collection("PostgreSQL")]
public sealed class ScenarioCommandSmokeTests(PostgresFixture fixture) : IAsyncLifetime
{
    private static readonly JsonSerializerOptions ScenarioJsonOptions = new() { PropertyNameCaseInsensitive = true };

    public Task InitializeAsync() => fixture.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    [Fact]
    public async Task Every_command_validation_input_reaches_expected_evidence()
    {
        var scenarios = await LoadScenariosAsync();
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();

        foreach (var scenario in scenarios)
        {
            foreach (var action in scenario.Version.Actions)
            {
                foreach (var pattern in action.Patterns)
                {
                    var attempt = await StartAsync(client, scenario.Id);
                    var first = await SendCommandAsync(client, attempt, pattern.ValidationInput);
                    Assert.Equal(attempt.StateVersion + 1, first.StateVersion);
                    Assert.Equal(first.RevealedEvidence.Select(x => x.Id).Distinct(StringComparer.Ordinal).Count(), first.RevealedEvidence.Length);
                    Assert.Equal(action.RevealsEvidenceIds.Count, first.RevealedEvidence.Count(x => action.RevealsEvidenceIds.Contains(x.Id, StringComparer.Ordinal)));

                    var eventsBeforeRepeat = await EventCountAsync(attempt.Id);
                    var repeat = await SendCommandAsync(client, first, pattern.ValidationInput);
                    Assert.Equal(first.StateVersion + 1, repeat.StateVersion);
                    Assert.Equal(repeat.RevealedEvidence.Select(x => x.Id).Distinct(StringComparer.Ordinal).Count(), repeat.RevealedEvidence.Length);
                    Assert.Equal(eventsBeforeRepeat + 1, await EventCountAsync(attempt.Id));
                }
            }
        }
    }

    [Fact]
    public async Task Command_validation_inputs_are_not_exposed_by_public_apis()
    {
        await using var factory = new TestServerFactory();
        using var client = factory.CreateClient();
        var scenarios = await client.GetFromJsonAsync<ScenarioListItem[]>("/api/scenarios");
        Assert.NotNull(scenarios);

        foreach (var scenario in scenarios!)
        {
            await AssertNoCommandMetadataAsync(await client.GetStringAsync("/api/scenarios"));
            await AssertNoCommandMetadataAsync(await client.GetStringAsync($"/api/scenarios/{scenario.Id}"));
            var attempt = await StartAsync(client, scenario.Id);
            await AssertNoCommandMetadataAsync(JsonSerializer.Serialize(attempt, new JsonSerializerOptions(JsonSerializerDefaults.Web)));
        }
    }

    private static async Task<PublicAttemptView> StartAsync(HttpClient client, string scenarioId)
    {
        var response = await client.PostAsync($"/api/scenarios/{scenarioId}/attempts", null);
        Assert.Equal(HttpStatusCode.Created, response.StatusCode);
        var attempt = await response.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.NotNull(attempt);
        return attempt!;
    }

    private static async Task<PublicAttemptView> SendCommandAsync(HttpClient client, PublicAttemptView attempt, string input)
    {
        var response = await client.PostAsJsonAsync($"/api/attempts/{attempt.Id}/commands",
            new ActionRequest(null, input, attempt.StateVersion, Guid.NewGuid().ToString("N")));
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var updated = await response.Content.ReadFromJsonAsync<PublicAttemptView>();
        Assert.NotNull(updated);
        return updated!;
    }

    private async Task<int> EventCountAsync(Guid attemptId)
    {
        await using var db = fixture.CreateContext();
        return await db.AttemptEvents.CountAsync(x => x.AttemptId == attemptId);
    }

    private static async Task AssertNoCommandMetadataAsync(string json)
    {
        using var document = JsonDocument.Parse(json);
        AssertNoCommandMetadata(document.RootElement);
        await Task.CompletedTask;
    }

    private static void AssertNoCommandMetadata(JsonElement element)
    {
        if (element.ValueKind == JsonValueKind.Object)
        {
            foreach (var property in element.EnumerateObject())
            {
                Assert.DoesNotContain(property.Name, ["validationInput", "commandValidationInput", "exampleInput", "sampleInput", "commandPattern", "pattern"], StringComparer.OrdinalIgnoreCase);
                AssertNoCommandMetadata(property.Value);
            }
        }
        else if (element.ValueKind == JsonValueKind.Array)
        {
            foreach (var item in element.EnumerateArray()) AssertNoCommandMetadata(item);
        }
    }

    private static async Task<IReadOnlyList<Scenario>> LoadScenariosAsync()
    {
        var root = FindContentRoot();
        var scenarios = new List<Scenario>();
        foreach (var file in Directory.EnumerateFiles(root, "*.json", SearchOption.AllDirectories))
        {
            var scenario = JsonSerializer.Deserialize<Scenario>(await File.ReadAllTextAsync(file), ScenarioJsonOptions);
            Assert.NotNull(scenario);
            scenarios.Add(scenario!);
        }

        Assert.NotEmpty(scenarios);
        _ = ScenarioCatalog.Create(scenarios);
        return scenarios;
    }

    private static string FindContentRoot()
    {
        for (var directory = new DirectoryInfo(Directory.GetCurrentDirectory()); directory is not null; directory = directory.Parent)
        {
            var candidate = Path.Combine(directory.FullName, "content");
            if (Directory.Exists(candidate)) return candidate;
        }

        throw new DirectoryNotFoundException("Scenario content directory was not found.");
    }
}
