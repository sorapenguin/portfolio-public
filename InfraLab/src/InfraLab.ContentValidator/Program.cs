using System.Text.Json;
using InfraLab.Application;
using InfraLab.Domain;

var path = args.Length > 1 ? args[1] : Path.Combine("content", "lpic1", "linux-systemd-203-001.json");
try
{
    var scenario = JsonSerializer.Deserialize<Scenario>(await File.ReadAllTextAsync(path), new JsonSerializerOptions(JsonSerializerDefaults.Web));
    var errors = scenario is null
        ? new[] { $"{path}: JSON did not contain a scenario." }
        : new ScenarioDefinitionValidator().Validate(scenario)
            .Where(issue => issue.IsError)
            .Select(issue => $"{path}: {issue.Code}: {issue.Message}")
            .ToArray();

    Console.WriteLine(JsonSerializer.Serialize(new { valid = errors.Length == 0, errors }));
    Environment.ExitCode = errors.Length == 0 ? 0 : 1;
}
catch (Exception ex)
{
    Console.Error.WriteLine($"validator failure: {ex.Message}");
    Environment.ExitCode = 2;
}
