using System.Globalization;
using Calendo.Data;
using Calendo.Hubs;
using Calendo.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.RazorPages;
using Microsoft.AspNetCore.SignalR;
using Microsoft.EntityFrameworkCore;

namespace Calendo.Pages.Events;

[Authorize]
public class CreateModel : PageModel, IEventFormModel
{
    private readonly AppDbContext _db;
    private readonly UserManager<AppUser> _userManager;
    private readonly IHubContext<CalendarHub> _hub;

    public CreateModel(AppDbContext db, UserManager<AppUser> userManager, IHubContext<CalendarHub> hub)
    {
        _db = db;
        _userManager = userManager;
        _hub = hub;
    }

    [BindProperty]
    public EventInput Input { get; set; } = new();

    public IReadOnlyList<EventTypeOption> EventTypeOptions => EventTypeCatalog.Options;
    public List<UserOption> Users { get; set; } = new();

    public async Task OnGetAsync(string? start)
    {
        var startAt = TryParseStart(start, out var requestedStart)
            ? requestedStart
            : EventInput.GetDefaultStart(DateTime.Now);
        startAt = EventInput.TrimToMinute(startAt);
        Input.StartAt = startAt;
        Input.EndAt = startAt.AddMinutes(60);
        await LoadUsersAsync();
    }

    public async Task<IActionResult> OnPostAsync()
    {
        Input.NormalizeTimes();
        await LoadUsersAsync();
        ModelState.ClearValidationState(nameof(Input));
        if (!TryValidateModel(Input, nameof(Input)))
        {
            return Page();
        }
        if (!await ValidateSelectedUsersAsync())
        {
            return Page();
        }

        var currentUser = await _userManager.GetUserAsync(User);
        if (currentUser == null)
        {
            return Challenge();
        }
        EventTypeCatalog.TryGet(Input.EventTypeKey, out var eventType);

        var calendarEvent = new CalendarEvent
        {
            Title = eventType.Title,
            Description = EventTypeCatalog.DemoDescription,
            StartAt = ToUtc(Input.StartAt!.Value),
            EndAt = ToUtc(Input.EndAt!.Value),
            IsAllDay = Input.IsAllDay,
            CreatedById = currentUser.Id
        };
        foreach (var userId in Input.SelectedUserIds.Distinct())
        {
            calendarEvent.Attendees.Add(new EventAttendee { UserId = userId });
        }

        _db.CalendarEvents.Add(calendarEvent);
        await _db.SaveChangesAsync();
        await _hub.Clients.All.SendAsync("EventChanged");
        return RedirectToPage("/Index");
    }

    private async Task LoadUsersAsync()
    {
        Users = await _userManager.Users
            .OrderBy(u => u.DisplayName)
            .Select(u => new UserOption(u.Id, u.DisplayName, u.Color))
            .ToListAsync();
    }

    private async Task<bool> ValidateSelectedUsersAsync()
    {
        var selectedIds = Input.SelectedUserIds.Distinct().ToArray();
        var existingCount = await _userManager.Users.CountAsync(user => selectedIds.Contains(user.Id));
        if (existingCount == selectedIds.Length)
        {
            return true;
        }

        ModelState.AddModelError(
            "Input.SelectedUserIds",
            "選択された参加者に利用できないユーザーが含まれています。");
        return false;
    }

    private static DateTime ToUtc(DateTime value) =>
        DateTime.SpecifyKind(value, DateTimeKind.Local).ToUniversalTime();

    private static bool TryParseStart(string? value, out DateTime start)
    {
        if (DateTime.TryParseExact(
            value,
            "yyyy-MM-dd'T'HH:mm",
            CultureInfo.InvariantCulture,
            DateTimeStyles.None,
            out start))
        {
            return true;
        }

        start = default;
        return false;
    }
}
