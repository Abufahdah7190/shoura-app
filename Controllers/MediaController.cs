using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Shoura.API.Controllers;

[ApiController]
[Authorize]
[Route("api/media")]
public sealed class MediaController(IConfiguration configuration, IWebHostEnvironment environment) : ControllerBase
{
    private const long MaxFileSize = 100 * 1024 * 1024;
    private static readonly Dictionary<string, string> AllowedExtensions = new(StringComparer.OrdinalIgnoreCase)
    {
        [".mp4"] = "video", [".webm"] = "video", [".mov"] = "video",
        [".mp3"] = "audio", [".m4a"] = "audio", [".aac"] = "audio",
        [".wav"] = "audio", [".ogg"] = "audio", [".opus"] = "audio"
    };

    [HttpPost("upload")]
    [Consumes("multipart/form-data")]
    [RequestSizeLimit(MaxFileSize)]
    public async Task<ActionResult<MediaUploadDto>> Upload([FromForm] IFormFile file, CancellationToken cancellationToken)
    {
        if (!int.TryParse(User.FindFirstValue(ClaimTypes.NameIdentifier), out var userId) || userId <= 0)
            return Unauthorized();
        if (file is null || file.Length == 0) return BadRequest(new { message = "اختر ملفًا غير فارغ." });
        if (file.Length > MaxFileSize) return StatusCode(StatusCodes.Status413PayloadTooLarge);

        var extension = Path.GetExtension(Path.GetFileName(file.FileName));
        if (!AllowedExtensions.TryGetValue(extension, out var type))
            return BadRequest(new { message = "نوع الملف غير مدعوم." });
        var expectedPrefix = type + "/";
        if (!file.ContentType.StartsWith(expectedPrefix, StringComparison.OrdinalIgnoreCase) && file.ContentType != "application/octet-stream")
            return BadRequest(new { message = "نوع الملف لا يطابق امتداده." });

        var configuredPath = configuration["Media:StoragePath"] ?? Environment.GetEnvironmentVariable("SHOURA_MEDIA_PATH");
        var root = string.IsNullOrWhiteSpace(configuredPath) ? Path.Combine(environment.ContentRootPath, "uploads") : Path.GetFullPath(configuredPath);
        Directory.CreateDirectory(root);
        var storedName = $"{Guid.NewGuid():N}{extension.ToLowerInvariant()}";
        var destination = Path.Combine(root, storedName);
        await using (var output = System.IO.File.Create(destination))
            await file.CopyToAsync(output, cancellationToken);

        return Ok(new MediaUploadDto($"/media/{storedName}", type, file.Length));
    }
}

public sealed record MediaUploadDto(string Url, string MediaType, long SizeBytes);
