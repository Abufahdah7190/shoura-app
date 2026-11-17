using System.Security.Claims;
using System.Text.Encodings.Web;
using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Options;

namespace Shoura.API.Security;

public sealed class BearerTokenHandler : AuthenticationHandler<AuthenticationSchemeOptions>
{
    private readonly BearerTokenService _tokens;

    public BearerTokenHandler(
        IOptionsMonitor<AuthenticationSchemeOptions> options,
        ILoggerFactory logger,
        UrlEncoder encoder,
        BearerTokenService tokens) : base(options, logger, encoder)
    {
        _tokens = tokens;
    }

    protected override Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        var header = Request.Headers.Authorization.ToString();
        if (!header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
            return Task.FromResult(AuthenticateResult.NoResult());

        if (!_tokens.TryValidate(header[7..].Trim(), out var userId))
            return Task.FromResult(AuthenticateResult.Fail("Invalid or expired access token."));

        var identity = new ClaimsIdentity(
            [new Claim(ClaimTypes.NameIdentifier, userId.ToString())], Scheme.Name);
        var principal = new ClaimsPrincipal(identity);
        return Task.FromResult(AuthenticateResult.Success(new AuthenticationTicket(principal, Scheme.Name)));
    }
}
