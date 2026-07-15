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
public class DetailModel : PageModel
{
    private readonly AppDbContext _db;
    private readonly UserManager<AppUser> _userManager;
    private readonly IHubContext<CalendarHub> _hub;

    public DetailModel(AppDbContext db, UserManager<AppUser> userManager, IHubContext<CalendarHub> hub)
    {
        _db = db;
        _userManager = userManager;
        _hub = hub;
    }

    public CalendarEvent? CalendarEvent { get; set; }
    public EventAttendee? CurrentAttendee { get; set; }
    public bool CanEdit { get; set; }

    public async Task<IActionResult> OnGetAsync(int id)
    {
        var loaded = await LoadAsync(id);
        return loaded ? Page() : NotFound();
    }

    public async Task<IActionResult> OnPostDeleteAsync(int id)
    {
        var currentUser = await _userManager.GetUserAsync(User);
        var calendarEvent = await _db.CalendarEvents.FindAsync(id);
        if (calendarEvent == null)
        {
            return NotFound();
        }
        if (currentUser == null || calendarEvent.CreatedById != currentUser.Id)
        {
            return Forbid();
        }

        _db.CalendarEvents.Remove(calendarEvent);
        await _db.SaveChangesAsync();
        await _hub.Clients.All.SendAsync("EventChanged");
        return RedirectToPage("/Index");
    }

    public async Task<IActionResult> OnPostStatusAsync(int id, AttendeeStatus status)
    {
        if (status is not AttendeeStatus.Accepted and not AttendeeStatus.Declined)
        {
            return BadRequest();
        }

        var currentUser = await _userManager.GetUserAsync(User);
        if (currentUser == null)
        {
            return Challenge();
        }

        var attendee = await _db.EventAttendees.FindAsync(id, currentUser.Id);
        if (attendee == null)
        {
            return Forbid();
        }

        attendee.Status = status;
        await _db.SaveChangesAsync();
        await _hub.Clients.All.SendAsync("EventChanged");
        return RedirectToPage("/Events/Detail", new { id });
    }

    private async Task<bool> LoadAsync(int id)
    {
        CalendarEvent = await _db.CalendarEvents
            .Include(e => e.CreatedBy)
            .Include(e => e.Attendees)
            .ThenInclude(a => a.User)
            .FirstOrDefaultAsync(e => e.Id == id);
        if (CalendarEvent == null)
        {
            return false;
        }

        var currentUser = await _userManager.GetUserAsync(User);
        CanEdit = currentUser != null && CalendarEvent.CreatedById == currentUser.Id;
        CurrentAttendee = currentUser == null
            ? null
            : CalendarEvent.Attendees.FirstOrDefault(a => a.UserId == currentUser.Id);
        return true;
    }
}
