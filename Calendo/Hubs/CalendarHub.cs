using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.SignalR;

namespace Calendo.Hubs;

[Authorize]
public class CalendarHub : Hub
{
}
