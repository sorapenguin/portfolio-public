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
public class EditModel : PageModel, IEventFormModel
{
    private readonly AppDbContext _db;
    private readonly UserManager<AppUser> _userManager;
    private readonly IHubContext<CalendarHub> _hub;

    public EditModel(AppDbContext db, UserManager<AppUser> userManager, IHubContext<CalendarHub> hub)
    {
        _db = db;
        _userManager = userManager;
        _hub = hub;
    }

    [BindProperty]
    public EventInput Input { get; set; } = new();

    public int EventId { get; set; }
    public IReadOnlyList<EventTypeOption> EventTypeOptions => EventTypeCatalog.Options;
    public List<UserOption> Users { get; set; } = new();

    public async Task<IActionResult> OnGetAsync(int id)
    {
        var currentUser = await _userManager.GetUserAsync(User);
        var calendarEvent = await _db.CalendarEvents.Include(e => e.Attendees).FirstOrDefaultAsync(e => e.Id == id);
        if (calendarEvent == null)
        {
            return NotFound();
        }
        if (currentUser == null || calendarEvent.CreatedById != currentUser.Id)
        {
            return Forbid();
        }

        EventId = id;
        Input = new EventInput
        {
            EventTypeKey = EventTypeCatalog.GetKeyForTitle(calendarEvent.Title),
            StartAt = EventInput.TrimToMinute(ToLocal(calendarEvent.StartAt)),
            EndAt = EventInput.TrimToMinute(ToLocal(calendarEvent.EndAt)),
            IsAllDay = calendarEvent.IsAllDay,
            SelectedUserIds = calendarEvent.Attendees.Select(a => a.UserId).ToList()
        };
        await LoadUsersAsync();
        return Page();
    }

    public async Task<IActionResult> OnPostAsync(int id)
    {
        EventId = id;
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
        var calendarEvent = await _db.CalendarEvents.Include(e => e.Attendees).FirstOrDefaultAsync(e => e.Id == id);
        if (calendarEvent == null)
        {
            return NotFound();
        }
        if (currentUser == null || calendarEvent.CreatedById != currentUser.Id)
        {
            return Forbid();
        }
        EventTypeCatalog.TryGet(Input.EventTypeKey, out var eventType);

        calendarEvent.Title = eventType.Title;
        calendarEvent.Description = EventTypeCatalog.DemoDescription;
        calendarEvent.StartAt = ToUtc(Input.StartAt!.Value);
        calendarEvent.EndAt = ToUtc(Input.EndAt!.Value);
        calendarEvent.IsAllDay = Input.IsAllDay;
        calendarEvent.UpdatedAt = DateTime.UtcNow;

        var selected = Input.SelectedUserIds.Distinct().ToHashSet();
        _db.EventAttendees.RemoveRange(calendarEvent.Attendees.Where(a => !selected.Contains(a.UserId)));
        foreach (var userId in selected.Except(calendarEvent.Attendees.Select(a => a.UserId)))
        {
            calendarEvent.Attendees.Add(new EventAttendee { EventId = id, UserId = userId });
        }

        await _db.SaveChangesAsync();
        await _hub.Clients.All.SendAsync("EventChanged");
        return RedirectToPage("/Events/Detail", new { id });
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

    private static DateTime ToLocal(DateTime value) =>
        DateTime.SpecifyKind(value, DateTimeKind.Utc).ToLocalTime();

    private static DateTime ToUtc(DateTime value) =>
        DateTime.SpecifyKind(value, DateTimeKind.Local).ToUniversalTime();
}
