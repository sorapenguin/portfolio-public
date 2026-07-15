using System.Text.Json;
using InfraLab.Domain;

var path = args.Length > 1 ? args[1] : Path.Combine("content", "lpic1", "linux-systemd-203-001.json");
try
{
    var scenario = JsonSerializer.Deserialize<Scenario>(await File.ReadAllTextAsync(path), new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
    var errors = new List<string>();
    if (scenario is null) errors.Add("JSONを読み込めません。");
    else { var actions = scenario.Version.Actions.Select(a => a.Id).ToHashSet(); if (string.IsNullOrWhiteSpace(scenario.Id) || string.IsNullOrWhiteSpace(scenario.Title)) errors.Add("IDまたはタイトルがありません。"); if (scenario.Version.Actions.Any(a => a.Patterns.Count == 0)) errors.Add("コマンドパターンのないActionがあります。"); if (!scenario.Version.Actions.Any(a => a.Id == "ls-permissions")) errors.Add("必要な権限確認Actionがありません。"); if (!scenario.Version.Private.RequiredEvidenceIds.All(id => scenario.Version.Evidence.Any(e => e.Id == id))) errors.Add("不存在Evidence参照があります。"); if (!scenario.Version.Diagnoses.Any(x => x.Id == scenario.Version.Private.CorrectDiagnosisId)) errors.Add("正答Diagnosisがありません。"); if (!scenario.Version.Remediations.Any(x => x.Id == scenario.Version.Private.CorrectRemediationId)) errors.Add("正答Remediationがありません。"); if (scenario.Version.Private.RequiredVerificationIds.Count == 0) errors.Add("復旧確認がありません。"); if (scenario.Version.Private.ExemplaryPath.Any(id => !actions.Contains(id))) errors.Add("模範経路に不存在Actionがあります。"); }
    Console.WriteLine(JsonSerializer.Serialize(new { valid = errors.Count == 0, errors })); Environment.ExitCode = errors.Count == 0 ? 0 : 1;
}
catch (Exception ex) { Console.Error.WriteLine($"validator failure: {ex.Message}"); Environment.ExitCode = 2; }
