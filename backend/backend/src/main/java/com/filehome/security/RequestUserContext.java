package com.filehome.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import com.filehome.user.UserAccount;

@Component
public class RequestUserContext {

    public static final String USER_ATTRIBUTE = "auth.user";

    public Optional<UserAccount> current(UUID userId) {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        Object value = attributes.getAttribute(
                USER_ATTRIBUTE,
                org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        if (value instanceof UserAccount account
                && account.id().equals(userId)) {
            return Optional.of(account);
        }
        return Optional.empty();
    }
}
