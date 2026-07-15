using System.Text.Json;
using InfraLab.Application;
using InfraLab.Domain;

namespace InfraLab.Api.Tests;

public sealed class ScenarioValidationTests
{
    private static readonly JsonSerializerOptions ScenarioJsonOptions = new() { PropertyNameCaseInsensitive = true };
    private static readonly ScenarioDefinitionValidator Validator = new();

    [Fact]
    public async Task All_scenarios_pass_semantic_validation()
    {
        var scenarios = await LoadScenariosAsync();

        Assert.NotEmpty(scenarios);
        Assert.Equal(scenarios.Count, scenarios.Select(x => x.Id).Distinct(StringComparer.Ordinal).Count());

        foreach (var scenario in scenarios)
        {
            var errors = Validator.Validate(scenario).Where(x => x.IsError).ToArray();
            Assert.True(errors.Length == 0, $"Scenario {scenario.Id}: {string.Join(", ", errors.Select(x => $"{x.Code}: {x.Message}"))}");
        }
    }

    [Fact]
    public async Task Validator_accepts_current_valid_scenario()
    {
        var scenario = (await LoadScenariosAsync()).First();

        Assert.DoesNotContain(Validator.Validate(scenario), x => x.IsError);
    }

    [Fact]
    public async Task Valid_scenario_corpus_loads_successfully()
    {
        var scenarios = await LoadScenariosAsync();

        var catalog = ScenarioCatalog.Create(scenarios);

        Assert.NotEmpty(catalog.Scenarios);
        Assert.Equal(scenarios.Count, catalog.Scenarios.Count);
    }

    [Fact]
    public async Task Invalid_scenario_is_rejected_before_catalog_registration()
    {
        var scenario = (await LoadScenariosAsync()).First();
        var action = scenario.Version.Actions[0] with { RevealsEvidenceIds = ["missing-reference"] };
        var invalid = scenario with { Version = scenario.Version with { Actions = [action, .. scenario.Version.Actions.Skip(1)] } };

        var exception = Assert.Throws<ScenarioCatalogValidationException>(() => ScenarioCatalog.Create([invalid]));

        Assert.Contains("REQUIRED_REFERENCE_NOT_FOUND", exception.Message, StringComparison.Ordinal);
        Assert.DoesNotContain("missing-reference", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public async Task Duplicate_scenario_ids_are_rejected_before_registration()
    {
        var scenario = (await LoadScenariosAsync()).First();
        var duplicate = scenario with { Title = "Another scenario title" };

        var exception = Assert.Throws<ScenarioCatalogValidationException>(() => ScenarioCatalog.Create([scenario, duplicate]));

        Assert.Contains("DUPLICATE_SCENARIO_ID", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public async Task Validator_rejects_duplicate_candidate_ids()
    {
        var scenario = (await LoadScenariosAsync()).First();
        var diagnosis = scenario.Version.Diagnoses[0];
        var invalid = scenario with
        {
            Version = scenario.Version with { Diagnoses = scenario.Version.Diagnoses.Concat([diagnosis]).ToArray() }
        };

        Assert.Contains(Validator.Validate(invalid), x => x.Code == "DUPLICATE_CANDIDATE_ID");
    }

    [Fact]
    public async Task Validator_rejects_missing_references()
    {
        var scenario = (await LoadScenariosAsync()).First();
        var action = scenario.Version.Actions[0] with { RevealsEvidenceIds = ["missing-reference"] };
        var invalid = scenario with
        {
            Version = scenario.Version with { Actions = [action, .. scenario.Version.Actions.Skip(1)] }
        };

        Assert.Contains(Validator.Validate(invalid), x => x.Code == "REQUIRED_REFERENCE_NOT_FOUND");
    }

    [Theory]
    [InlineData(RequiredVerificationCase.Empty)]
    [InlineData(RequiredVerificationCase.Duplicate)]
    [InlineData(RequiredVerificationCase.Unknown)]
    public async Task Validator_rejects_invalid_verification_required_set(RequiredVerificationCase testCase)
    {
        var scenario = (await LoadScenariosAsync()).First();
        var required = testCase switch
        {
            RequiredVerificationCase.Empty => new HashSet<string>(),
            RequiredVerificationCase.Duplicate => DuplicateRequiredVerificationIds(),
            RequiredVerificationCase.Unknown => new HashSet<string>(["missing-required-verification"]),
            _ => throw new ArgumentOutOfRangeException(nameof(testCase))
        };
        var invalid = scenario with
        {
            Version = scenario.Version with { Private = scenario.Version.Private with { RequiredVerificationIds = required } }
        };
        var expectedCode = testCase switch
        {
            RequiredVerificationCase.Empty => "VERIFICATION_REQUIRED_EMPTY",
            RequiredVerificationCase.Duplicate => "VERIFICATION_REQUIRED_DUPLICATE",
            _ => "VERIFICATION_REQUIRED_NOT_FOUND"
        };

        Assert.Contains(Validator.Validate(invalid), x => x.Code == expectedCode);
    }

    [Fact]
    public async Task Validator_rejects_empty_required_text()
    {
        var scenario = (await LoadScenariosAsync()).First() with { Title = " " };

        Assert.Contains(Validator.Validate(scenario), x => x.Code == "USER_TEXT_REQUIRED");
    }

    [Fact]
    public async Task Validator_rejects_duplicate_command_patterns()
    {
        var scenario = (await LoadScenariosAsync()).First();
        var original = scenario.Version.Actions.First(x => x.Patterns.Count > 0);
        var action = original with { Patterns = [original.Patterns[0], original.Patterns[0]] };
        var invalid = scenario with
        {
            Version = scenario.Version with
            {
                Actions = scenario.Version.Actions.Select(x => x.Id == original.Id ? action : x).ToArray()
            }
        };

        Assert.Contains(Validator.Validate(invalid), x => x.Code == "COMMAND_PATTERN_DUPLICATE");
    }

    [Fact]
    public async Task Validator_rejects_missing_command_validation_input()
    {
        var scenario = (await LoadScenariosAsync()).First();
        var action = scenario.Version.Actions[0] with { Patterns = [scenario.Version.Actions[0].Patterns[0] with { ValidationInput = " " }] };
        var invalid = scenario with { Version = scenario.Version with { Actions = [action, .. scenario.Version.Actions.Skip(1)] } };

        Assert.Contains(Validator.Validate(invalid), x => x.Code == "COMMAND_VALIDATION_INPUT_REQUIRED");
    }

    [Fact]
    public async Task Validator_rejects_command_validation_input_that_matches_no_pattern()
    {
        var scenario = (await LoadScenariosAsync()).First();
        var action = scenario.Version.Actions[0] with { Patterns = [scenario.Version.Actions[0].Patterns[0] with { ValidationInput = "unmatched-validation-command" }] };
        var invalid = scenario with { Version = scenario.Version with { Actions = [action, .. scenario.Version.Actions.Skip(1)] } };

        Assert.Contains(Validator.Validate(invalid), x => x.Code == "COMMAND_VALIDATION_INPUT_NOT_MATCHED");
    }

    [Fact]
    public async Task Validator_rejects_ambiguous_command_validation_input()
    {
        var scenario = (await LoadScenariosAsync()).First();
        var first = scenario.Version.Actions[0];
        var second = scenario.Version.Actions[1] with { Patterns = [new CommandPattern(first.Patterns[0].Value, first.Patterns[0].ValidationInput)] };
        var invalid = scenario with { Version = scenario.Version with { Actions = [first, second, .. scenario.Version.Actions.Skip(2)] } };

        Assert.Contains(Validator.Validate(invalid), x => x.Code == "COMMAND_VALIDATION_INPUT_AMBIGUOUS");
    }

    [Fact]
    public async Task Validator_rejects_duplicate_normalized_command_validation_inputs()
    {
        var scenario = (await LoadScenariosAsync()).First();
        var first = scenario.Version.Actions[0];
        var duplicate = new CommandPattern(first.Patterns[0].Value, $"sudo {first.Patterns[0].ValidationInput}");
        var action = first with { Patterns = [first.Patterns[0], duplicate] };
        var invalid = scenario with { Version = scenario.Version with { Actions = [action, .. scenario.Version.Actions.Skip(1)] } };

        Assert.Contains(Validator.Validate(invalid), x => x.Code == "COMMAND_VALIDATION_INPUT_DUPLICATE");
    }

    private static async Task<IReadOnlyList<Scenario>> LoadScenariosAsync()
    {
        var contentRoot = FindContentRoot();
        var scenarios = new List<Scenario>();
        foreach (var file in Directory.EnumerateFiles(contentRoot, "*.json", SearchOption.AllDirectories))
        {
            var scenario = JsonSerializer.Deserialize<Scenario>(await File.ReadAllTextAsync(file), ScenarioJsonOptions);
            Assert.NotNull(scenario);
            scenarios.Add(scenario!);
        }

        return scenarios;
    }

    private static string FindContentRoot()
    {
        foreach (var start in new[] { Directory.GetCurrentDirectory(), AppContext.BaseDirectory })
        {
            for (var directory = new DirectoryInfo(start); directory is not null; directory = directory.Parent)
            {
                var candidate = Path.Combine(directory.FullName, "content");
                if (Directory.Exists(candidate))
                {
                    return candidate;
                }
            }
        }

        throw new DirectoryNotFoundException("Scenario content directory was not found.");
    }

    private static HashSet<string> DuplicateRequiredVerificationIds()
    {
        var ids = new HashSet<string>(NeverEqualStringComparer.Instance);
        ids.Add("duplicate-required-verification");
        ids.Add("duplicate-required-verification");
        return ids;
    }

    public enum RequiredVerificationCase { Empty, Duplicate, Unknown }

    private sealed class NeverEqualStringComparer : IEqualityComparer<string>
    {
        public static readonly NeverEqualStringComparer Instance = new();

        public bool Equals(string? x, string? y) => false;
        public int GetHashCode(string obj) => StringComparer.Ordinal.GetHashCode(obj);
    }
}
