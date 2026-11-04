package com.filehome.security;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class ClientAddressResolver {

    private static final Pattern IPV4_OR_LOCAL =
            Pattern.compile("^[0-9a-fA-F:.]{3,45}$");

    public String resolve(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && IPV4_OR_LOCAL.matcher(realIp.trim()).matches()) {
            return realIp.trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
