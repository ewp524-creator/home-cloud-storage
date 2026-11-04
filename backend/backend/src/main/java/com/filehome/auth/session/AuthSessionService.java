package com.filehome.auth.session;

import java.time.Instant;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;

import com.filehome.config.AppProperties;
import com.filehome.security.ClientAddressResolver;
import com.filehome.user.UserAccount;
import com.filehome.user.UserProfileChangedEvent;
import com.filehome.user.UserAccountDeactivatedEvent;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuthSessionService {

    private static final int MAX_USER_AGENT_LENGTH = 512;

    private final AuthSessionRepository repository;
    private final SessionTokenService tokenService;
    private final SessionResolutionCache resolutionCache;
    private final AuthCookieService cookieService;
    private final ClientAddressResolver addressResolver;
    private final long sessionHours;

    public AuthSessionService(
            AuthSessionRepository repository,
            SessionTokenService tokenService,
            SessionResolutionCache resolutionCache,
            AuthCookieService cookieService,
            ClientAddressResolver addressResolver,
            AppProperties properties) {
        this.repository = repository;
        this.tokenService = tokenService;
        this.resolutionCache = resolutionCache;
        this.cookieService = cookieService;
        this.addressResolver = addressResolver;
        if (properties.auth().sessionHours() <= 0) {
            throw new IllegalStateException(
                    "FILEHOME_SESSION_HOURS must be positive");
        }
        this.sessionHours = properties.auth().sessionHours();
    }

    public BrowserSession issue(
            UserAccount user,
            HttpServletRequest request) {
        String rawToken = tokenService.newToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(sessionHours * 3600);
        AuthSession session = new AuthSession(
                UUID.randomUUID(),
                user.id(),
                tokenService.hash(rawToken),
                truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH),
                tokenService.hash(addressResolver.resolve(request)),
                now,
                expiresAt);
        repository.insert(session);
        return new BrowserSession(
                rawToken,
                tokenService.csrfFor(rawToken),
                expiresAt);
    }

    public Optional<ResolvedSession> resolve(HttpServletRequest request) {
        String rawToken = cookieService.readSessionToken(request);
        if (rawToken.isBlank() || rawToken.length() > 4096) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        String tokenHash = tokenService.hashHex(rawToken);
        Optional<AuthSessionAccountRow> row = resolutionCache.get(tokenHash, now);
        if (row.isEmpty()) {
            row = repository.findActiveByTokenHash(tokenService.hash(rawToken));
            row.ifPresent(active -> resolutionCache.put(tokenHash, active, now));
            row.ifPresent(active -> repository.touchIfStale(active.sessionId()));
        }
        return row.map(active -> new ResolvedSession(
                active.toPrincipal(),
                rawToken));
    }

    public void writeCookies(
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            BrowserSession session) {
        cookieService.write(
                response,
                session.token(),
                session.csrfToken(),
                session.expiresAt());
    }

    public void ensureCsrfCookie(
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            ResolvedSession session) {
        if (cookieService.readCsrfToken(request).isBlank()) {
            cookieService.writeCsrf(
                    response,
                    tokenService.csrfFor(session.rawToken()),
                    Duration.between(
                            Instant.now(),
                            session.principal().expiresAt()));
        }
    }

    public boolean csrfMatches(HttpServletRequest request, String rawToken) {
        return tokenService.csrfMatches(
                rawToken,
                request.getHeader("X-CSRF-Token"));
    }

    public void revoke(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            Instant now = Instant.now();
            resolutionCache.revoke(
                    tokenService.hashHex(rawToken),
                    now.plusSeconds(sessionHours * 3600),
                    now);
            repository.revokeByTokenHash(tokenService.hash(rawToken));
        }
    }

    public void revoke(UUID userId, UUID sessionId) {
        repository.findActiveTokenByIdAndUser(sessionId, userId).ifPresent(token -> {
            Instant now = Instant.now();
            resolutionCache.revoke(
                    HexFormat.of().formatHex(token.tokenHash()),
                    token.expiresAt(),
                    now);
        });
        repository.revokeByIdAndUser(sessionId, userId);
    }

    public int revokeOthers(UUID userId, UUID currentSessionId) {
        Instant now = Instant.now();
        repository.findActiveTokensByUserExcept(userId, currentSessionId)
                .forEach(token -> resolutionCache.revoke(
                        HexFormat.of().formatHex(token.tokenHash()),
                        token.expiresAt(),
                        now));
        return repository.revokeOthers(userId, currentSessionId);
    }

    public List<AuthSessionView> list(UUID userId, UUID currentSessionId) {
        return repository.listActive(userId, currentSessionId);
    }

    public int deleteExpired() {
        return repository.deleteExpired(1000);
    }

    @EventListener
    public void invalidateForProfileChange(UserProfileChangedEvent event) {
        repository.findActiveTokensByUser(event.userId())
                .forEach(token -> resolutionCache.invalidate(
                        HexFormat.of().formatHex(token.tokenHash())));
    }

    @EventListener
    public void revokeForDeactivation(UserAccountDeactivatedEvent event) {
        Instant now = Instant.now();
        repository.findActiveTokensByUser(event.userId())
                .forEach(token -> resolutionCache.revoke(
                        HexFormat.of().formatHex(token.tokenHash()),
                        token.expiresAt(),
                        now));
        repository.revokeAllByUser(event.userId());
    }

    public void clearCookies(jakarta.servlet.http.HttpServletResponse response) {
        cookieService.clear(response);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }

    public record BrowserSession(
            String token,
            String csrfToken,
            Instant expiresAt) {
    }

    public record ResolvedSession(
            AuthSessionPrincipal principal,
            String rawToken) {
    }
}
