package com.filehome.auth;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.filehome.auth.session.AuthSessionService;
import com.filehome.auth.session.AuthSessionView;
import com.filehome.security.ClientAddressResolver;
import com.filehome.security.RequestRateLimiter;
import com.filehome.security.RequestUserContext;
import com.filehome.user.UserAccount;
import com.filehome.user.UserView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthSessionService sessionService;
    private final RequestRateLimiter rateLimiter;
    private final ClientAddressResolver addressResolver;
    private final CaptchaService captchaService;

    public AuthController(
            AuthService authService,
            AuthSessionService sessionService,
            RequestRateLimiter rateLimiter,
            ClientAddressResolver addressResolver,
            CaptchaService captchaService) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.rateLimiter = rateLimiter;
        this.addressResolver = addressResolver;
        this.captchaService = captchaService;
    }

    @GetMapping("/captcha")
    public ResponseEntity<CaptchaService.Challenge> captcha(
            HttpServletRequest servletRequest) {
        String address = addressResolver.resolve(servletRequest);
        rateLimiter.check(
                "captcha:" + address,
                30,
                Duration.ofMinutes(1));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(captchaService.issue());
    }

    @PostMapping("/login")
    public BrowserAuthResponse login(
            @Valid @RequestBody BrowserLoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        String address = addressResolver.resolve(servletRequest);
        rateLimiter.check(
                "login:" + address + ":"
                        + request.account().trim().toLowerCase(Locale.ROOT),
                10,
                Duration.ofMinutes(5));
        captchaService.verify(request.captchaId(), request.captchaAnswer());
        UserAccount user = authService.authenticate(
                request.account(),
                request.password());
        AuthSessionService.BrowserSession session =
                sessionService.issue(user, servletRequest);
        sessionService.writeCookies(servletRequest, servletResponse, session);
        return new BrowserAuthResponse(UserView.from(user));
    }

    @PostMapping("/cli/login")
    public AuthService.AuthResponse cliLogin(
            @Valid @RequestBody CliLoginRequest request,
            HttpServletRequest servletRequest) {
        String address = addressResolver.resolve(servletRequest);
        rateLimiter.check(
                "cli-login:" + address + ":"
                        + request.account().trim().toLowerCase(Locale.ROOT),
                10,
                Duration.ofMinutes(5));
        return authService.cliResponse(authService.authenticate(
                request.account(),
                request.password()));
    }

    @PostMapping("/register")
    public BrowserAuthResponse register(
            @Valid @RequestBody BrowserRegisterRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        rateLimiter.check(
                "register:" + addressResolver.resolve(servletRequest),
                5,
                Duration.ofHours(1));
        captchaService.verify(request.captchaId(), request.captchaAnswer());
        UserAccount user = authService.register(
                request.username(),
                request.email(),
                request.password(),
                request.displayName());
        AuthSessionService.BrowserSession session =
                sessionService.issue(user, servletRequest);
        sessionService.writeCookies(servletRequest, servletResponse, session);
        return new BrowserAuthResponse(UserView.from(user));
    }

    @GetMapping("/session")
    public BrowserAuthResponse session(
            @RequestAttribute(RequestUserContext.USER_ATTRIBUTE) UserAccount user,
            HttpServletRequest request,
            HttpServletResponse response) {
        sessionService.resolve(request).ifPresent(session ->
                sessionService.ensureCsrfCookie(request, response, session));
        return new BrowserAuthResponse(UserView.from(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        String rawToken = (String) request.getAttribute(
                AuthInterceptor.SESSION_TOKEN_ATTRIBUTE);
        sessionService.revoke(rawToken);
        sessionService.clearCookies(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public List<AuthSessionView> sessions(
            @RequestAttribute(AuthInterceptor.USER_ID_ATTRIBUTE) UUID userId,
            HttpServletRequest request) {
        UUID currentSessionId = (UUID) request.getAttribute(
                AuthInterceptor.SESSION_ID_ATTRIBUTE);
        return sessionService.list(userId, currentSessionId);
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revokeSession(
            @RequestAttribute(AuthInterceptor.USER_ID_ATTRIBUTE) UUID userId,
            @PathVariable UUID id) {
        sessionService.revoke(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/revoke-others")
    public ResponseEntity<Void> revokeOtherSessions(
            @RequestAttribute(AuthInterceptor.USER_ID_ATTRIBUTE) UUID userId,
            HttpServletRequest request) {
        UUID currentSessionId = (UUID) request.getAttribute(
                AuthInterceptor.SESSION_ID_ATTRIBUTE);
        if (currentSessionId != null) {
            sessionService.revokeOthers(userId, currentSessionId);
        }
        return ResponseEntity.noContent().build();
    }

    public record BrowserAuthResponse(UserView user) {
    }

    public record BrowserLoginRequest(
            @NotBlank @Size(max = 254) String account,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 64) String captchaId,
            @NotBlank @Size(max = 12) String captchaAnswer) {
    }

    public record CliLoginRequest(
            @NotBlank @Size(max = 254) String account,
            @NotBlank @Size(min = 8, max = 128) String password) {
    }

    public record BrowserRegisterRequest(
            @NotBlank @Size(min = 2, max = 32) String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Size(max = 80) String displayName,
            @NotBlank @Size(max = 64) String captchaId,
            @NotBlank @Size(max = 12) String captchaAnswer) {
    }
}
