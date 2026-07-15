using Calendo.Data;
using Calendo.Hubs;
using Calendo.Models;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddDbContext<AppDbContext>(opt =>
    opt.UseNpgsql(builder.Configuration.GetConnectionString("DefaultConnection")));

builder.Services.AddIdentity<AppUser, IdentityRole>(opt =>
{
    opt.Password.RequireDigit = false;
    opt.Password.RequireUppercase = false;
    opt.Password.RequireNonAlphanumeric = false;
    opt.Password.RequiredLength = 6;
})
.AddEntityFrameworkStores<AppDbContext>()
.AddDefaultTokenProviders();

builder.Services.ConfigureApplicationCookie(opt =>
{
    opt.LoginPath = "/account/login";
    opt.AccessDeniedPath = "/";
});

builder.Services.AddSignalR();
builder.Services.AddRazorPages();

var app = builder.Build();

await DemoAccountSeeder.SeedAsync(app.Services);

if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Error");
    app.UseHsts();
}

app.UseHttpsRedirection();
app.UseStaticFiles();
app.UseRouting();
app.UseAuthentication();
app.UseAuthorization();

app.MapGet("/api/events", async (DateTime start, DateTime end, AppDbContext db) =>
{
    var startUtc = DateTime.SpecifyKind(start, DateTimeKind.Local).ToUniversalTime();
    var endUtc = DateTime.SpecifyKind(end, DateTimeKind.Local).ToUniversalTime();

    var events = await db.CalendarEvents
        .Include(e => e.CreatedBy)
        .Where(e => e.StartAt < endUtc && e.EndAt > startUtc)
        .OrderBy(e => e.StartAt)
        .Select(e => new
        {
            id = e.Id,
            title = e.Title,
            start = e.StartAt,
            end = e.EndAt,
            allDay = e.IsAllDay,
            color = e.CreatedBy.Color
        })
        .ToListAsync();

    return Results.Json(events);
}).RequireAuthorization();

app.MapHub<CalendarHub>("/calendarHub");
app.MapRazorPages();

app.Run();
