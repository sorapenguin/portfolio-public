namespace Calendo.Models;

public class EventAttendee
{
    public int EventId { get; set; }
    public CalendarEvent Event { get; set; } = null!;
    public string UserId { get; set; } = string.Empty;
    public AppUser User { get; set; } = null!;
    public AttendeeStatus Status { get; set; } = AttendeeStatus.Pending;
}

public enum AttendeeStatus
{
    Pending,
    Accepted,
    Declined
}
