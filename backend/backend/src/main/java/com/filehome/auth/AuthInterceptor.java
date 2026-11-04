package com.filehome.auth;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filehome.auth.RequestAuthenticator.AuthenticatedUser;
import com.filehome.auth.session.AuthSessionService;
import com.filehome.common.ApiError;
import com.filehome.common.ApiException;
import com.filehome.security.RequestUserContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID_ATTRIBUTE = "auth.userId";
    public static final String USERNAME_ATTRIBUTE = "auth.username";
    public static final String SESSION_ID_ATTRIBUTE = "auth.sessionId";
    public static final String SESSION_TOKEN_ATTRIBUTE = "auth.sessionToken";
    public static final String AUTH_METHOD_ATTRIBUTE = "auth.method";

    private static final Set<String> SAFE_METHODS =
            Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final RequestAuthenticator requestAuthenticator;
    private final AuthSessionService sessionService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(
            RequestAuthenticator requestAuthenticator,
            AuthSessionService sessionService,
            ObjectMapper objectMapper) {
        this.requestAuthenticator = requestAuthenticator;
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        try {
            AuthenticatedUser authenticated = requestAuthenticator.resolve(request)
                    .orElseThrow(() -> ApiException.unauthorized(
                            "\u8bf7\u5148\u767b\u5f55"));
            if (authenticated.method()
                    == RequestAuthenticator.AuthenticationMethod.SESSION
                    && !SAFE_METHODS.contains(request.getMethod().toUpperCase())
                    && !sessionService.csrfMatches(
                            request,
                            authenticated.rawSessionToken())) {
                writeError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        new ApiError(
                                "CSRF_INVALID",
                                "\u8bf7\u6c42\u6821\u9a8c\u5df2\u5931\u6548\uff0c\u8bf7\u5237\u65b0\u9875\u9762\u91cd\u8bd5"));
                return false;
            }
            request.setAttribute(
                    RequestUserContext.USER_ATTRIBUTE,
                    authenticated.user());
            request.setAttribute(USER_ID_ATTRIBUTE, authenticated.user().id());
            request.setAttribute(
                    USERNAME_ATTRIBUTE,
                    authenticated.user().username());
            request.setAttribute(
                    SESSION_ID_ATTRIBUTE,
                    authenticated.sessionId());
            request.setAttribute(
                    SESSION_TOKEN_ATTRIBUTE,
                    authenticated.rawSessionToken());
            request.setAttribute(
                    AUTH_METHOD_ATTRIBUTE,
                    authenticated.method());
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Vary", "Authorization, Cookie");
            return true;
        } catch (ApiException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    new ApiError("UNAUTHORIZED", exception.getMessage()));
            return false;
        }
    }

    public static java.util.UUID userId(HttpServletRequest request) {
        return (java.util.UUID) request.getAttribute(USER_ID_ATTRIBUTE);
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            ApiError error) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), error);
    }
}
