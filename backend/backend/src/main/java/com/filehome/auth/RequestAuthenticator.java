package com.filehome.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import com.filehome.auth.session.AuthSessionService;
import com.filehome.common.ApiException;
import com.filehome.user.UserAccount;
import com.filehome.user.UserService;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class RequestAuthenticator {

    private final JwtService jwtService;
    private final UserService userService;
    private final AuthSessionService sessionService;

    public RequestAuthenticator(
            JwtService jwtService,
            UserService userService,
            AuthSessionService sessionService) {
        this.jwtService = jwtService;
        this.userService = userService;
        this.sessionService = sessionService;
    }

    public Optional<AuthenticatedUser> resolve(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null) {
            return Optional.of(authenticateBearer(authorization));
        }
        return sessionService.resolve(request)
                .map(session -> new AuthenticatedUser(
                        session.principal().user(),
                        session.principal().sessionId(),
                        session.rawToken(),
                        AuthenticationMethod.SESSION));
    }

    private AuthenticatedUser authenticateBearer(String authorization) {
        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                || authorization.length() > 4096) {
            throw ApiException.unauthorized("\u8bf7\u5148\u767b\u5f55");
        }
        try {
            JwtService.AuthPrincipal principal = jwtService.parse(
                    authorization.substring("Bearer ".length()));
            UserAccount user = userService.requireEnabled(principal.userId());
            return new AuthenticatedUser(
                    user,
                    null,
                    null,
                    AuthenticationMethod.BEARER);
        } catch (JwtException | IllegalArgumentException exception) {
            throw ApiException.unauthorized(
                    "\u767b\u5f55\u72b6\u6001\u5df2\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55");
        }
    }

    public enum AuthenticationMethod {
        SESSION,
        BEARER
    }

    public record AuthenticatedUser(
            UserAccount user,
            UUID sessionId,
            String rawSessionToken,
            AuthenticationMethod method) {
    }
}
