using System.ComponentModel.DataAnnotations;

namespace Calendo.Pages.Events;

public interface IEventFormModel
{
    EventInput Input { get; set; }
    IReadOnlyList<EventTypeOption> EventTypeOptions { get; }
    List<UserOption> Users { get; set; }
}

public class EventInput : IValidatableObject
{
    [Required(ErrorMessage = "予定種別を選択してください。"), Display(Name = "予定種別")]
    public string EventTypeKey { get; set; } = EventTypeCatalog.DefaultKey;

    [Required(ErrorMessage = "開始日時を入力してください。"), Display(Name = "開始日時")]
    public DateTime? StartAt { get; set; }

    [Required(ErrorMessage = "終了日時を入力してください。"), Display(Name = "終了日時")]
    public DateTime? EndAt { get; set; }

    [Display(Name = "終日")]
    public bool IsAllDay { get; set; }

    public List<string> SelectedUserIds { get; set; } = new();

    public IEnumerable<ValidationResult> Validate(ValidationContext validationContext)
    {
        if (!EventTypeCatalog.TryGet(EventTypeKey, out _))
        {
            yield return new ValidationResult(
                "選択できない予定種別です。",
                new[] { nameof(EventTypeKey) });
        }

        if (StartAt.HasValue && EndAt.HasValue && EndAt.Value <= StartAt.Value)
        {
            yield return new ValidationResult(
                "終了日時は開始日時より後にしてください。",
                new[] { nameof(EndAt) });
        }
    }

    public void NormalizeTimes()
    {
        EventTypeKey = EventTypeKey?.Trim() ?? string.Empty;
        SelectedUserIds ??= new List<string>();
        if (StartAt.HasValue)
        {
            StartAt = TrimToMinute(StartAt.Value);
        }
        if (EndAt.HasValue)
        {
            EndAt = TrimToMinute(EndAt.Value);
        }
    }

    public static DateTime GetDefaultStart(DateTime now)
    {
        var trimmed = TrimToMinute(now);
        var remainder = trimmed.Minute % 30;
        if (remainder == 0)
        {
            return trimmed;
        }

        return trimmed.AddMinutes(30 - remainder);
    }

    public static DateTime TrimToMinute(DateTime value) =>
        new(value.Year, value.Month, value.Day, value.Hour, value.Minute, 0, value.Kind);
}

public static class EventTypeCatalog
{
    public const string DefaultKey = "meeting";
    public const string DemoDescription = "ポートフォリオデモ用の予定です。";

    public static readonly IReadOnlyList<EventTypeOption> Options = new[]
    {
        new EventTypeOption("meeting", "打ち合わせ"),
        new EventTypeOption("work", "作業時間"),
        new EventTypeOption("study", "学習時間"),
        new EventTypeOption("interview", "面談"),
        new EventTypeOption("maintenance", "メンテナンス"),
        new EventTypeOption("break", "休憩"),
        new EventTypeOption("reservation", "予約枠")
    };

    public static bool TryGet(string? key, out EventTypeOption option)
    {
        option = Options.FirstOrDefault(o => o.Key == key)!;
        return option != null;
    }

    public static string GetKeyForTitle(string? title)
    {
        var matched = Options.FirstOrDefault(o => o.Title == title);
        return matched?.Key ?? DefaultKey;
    }
}

public record EventTypeOption(string Key, string Title);
public record UserOption(string Id, string DisplayName, string Color);
