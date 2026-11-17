using Microsoft.EntityFrameworkCore;
using System.Threading.RateLimiting;
using System.Security.Cryptography;
using System.Text;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.DataProtection;
using Microsoft.AspNetCore.StaticFiles;
using Microsoft.Extensions.FileProviders;
using Shoura.API.Security;
using Shoura.API.Data;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.
builder.Services.AddOpenApi();

// SQLite للتطوير المحلي، وPostgreSQL للنشر متعدد النسخ.
var databaseProvider = builder.Configuration["Database:Provider"] ?? Environment.GetEnvironmentVariable("SHOURA_DATABASE_PROVIDER") ?? "Sqlite";
var connectionString = builder.Configuration.GetConnectionString("DefaultConnection") ?? "Data Source=shoura.db";
if (databaseProvider.Equals("Postgres", StringComparison.OrdinalIgnoreCase))
    connectionString = Environment.GetEnvironmentVariable("SHOURA_CONNECTION_STRING") ?? builder.Configuration.GetConnectionString("Postgres") ?? throw new InvalidOperationException("Set SHOURA_CONNECTION_STRING or ConnectionStrings:Postgres.");
builder.Services.AddDbContext<AppDbContext>(options =>
{
    if (databaseProvider.Equals("Postgres", StringComparison.OrdinalIgnoreCase)) options.UseNpgsql(connectionString);
    else if (databaseProvider.Equals("Sqlite", StringComparison.OrdinalIgnoreCase)) options.UseSqlite(connectionString);
    else throw new InvalidOperationException("Database:Provider must be Sqlite or Postgres.");
});

builder.Services.AddControllers();
builder.Services.AddHttpClient("Gemini", client => client.Timeout = TimeSpan.FromMinutes(3));
var keyRingPath = builder.Configuration["DataProtection:KeyRingPath"] ?? Environment.GetEnvironmentVariable("SHOURA_DATA_PROTECTION_PATH");
if (string.IsNullOrWhiteSpace(keyRingPath) && builder.Environment.IsDevelopment())
    keyRingPath = Path.Combine(Path.GetTempPath(), "shoura-data-protection");
if (!string.IsNullOrWhiteSpace(keyRingPath))
{
    Directory.CreateDirectory(keyRingPath);
    builder.Services.AddDataProtection()
        .PersistKeysToFileSystem(new DirectoryInfo(keyRingPath))
        .SetApplicationName("Shoura");
}
var configuredSigningKey = builder.Configuration["Authentication:SigningKey"] ?? Environment.GetEnvironmentVariable("SHOURA_AUTH_SIGNING_KEY");
if (string.IsNullOrWhiteSpace(configuredSigningKey) && !builder.Environment.IsDevelopment())
    throw new InvalidOperationException("Set SHOURA_AUTH_SIGNING_KEY to a random secret of at least 32 characters before running outside Development.");
var signingKey = string.IsNullOrWhiteSpace(configuredSigningKey)
    ? RandomNumberGenerator.GetBytes(32)
    : Encoding.UTF8.GetBytes(configuredSigningKey);
if (signingKey.Length < 32) throw new InvalidOperationException("The authentication signing key must be at least 32 bytes.");
builder.Services.AddSingleton(new BearerTokenService(signingKey));
builder.Services.AddAuthentication("Bearer").AddScheme<AuthenticationSchemeOptions, BearerTokenHandler>("Bearer", _ => { });
builder.Services.AddAuthorization();
builder.Services.AddRateLimiter(options =>
{
    options.AddPolicy("auth", context => RateLimitPartition.GetFixedWindowLimiter(
        context.Connection.RemoteIpAddress?.ToString() ?? "unknown",
        _ => new FixedWindowRateLimiterOptions
        {
            PermitLimit = 10,
            Window = TimeSpan.FromMinutes(1),
            QueueLimit = 0,
            AutoReplenishment = true
        }));
    options.AddPolicy("ai", context => RateLimitPartition.GetFixedWindowLimiter(
        context.Connection.RemoteIpAddress?.ToString() ?? "unknown",
        _ => new FixedWindowRateLimiterOptions
        {
            PermitLimit = 5,
            Window = TimeSpan.FromMinutes(1),
            QueueLimit = 0,
            AutoReplenishment = true
        }));
});

var app = builder.Build();

using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    await db.Database.MigrateAsync();
}

var configuredMediaPath = builder.Configuration["Media:StoragePath"] ?? Environment.GetEnvironmentVariable("SHOURA_MEDIA_PATH");
var mediaPath = string.IsNullOrWhiteSpace(configuredMediaPath)
    ? Path.Combine(builder.Environment.ContentRootPath, "uploads")
    : Path.GetFullPath(configuredMediaPath);
Directory.CreateDirectory(mediaPath);
var mediaTypes = new FileExtensionContentTypeProvider();
mediaTypes.Mappings[".opus"] = "audio/opus";
mediaTypes.Mappings[".m4a"] = "audio/mp4";
mediaTypes.Mappings[".aac"] = "audio/aac";
mediaTypes.Mappings[".webm"] = "video/webm";

// Configure the HTTP request pipeline.
app.UseHttpsRedirection();
app.UseDefaultFiles();
app.UseStaticFiles();
app.UseStaticFiles(new StaticFileOptions
{
    FileProvider = new PhysicalFileProvider(mediaPath),
    RequestPath = "/media",
    ContentTypeProvider = mediaTypes
});

app.UseRouting();
app.UseRateLimiter();
app.UseAuthentication();
app.UseAuthorization();

// ربط الـ Controllers الخاصة بمشروعنا
app.MapControllers();
app.MapOpenApi();
app.MapGet("/health", () => Results.Ok(new { status = "ok" })).AllowAnonymous();

app.Run();
