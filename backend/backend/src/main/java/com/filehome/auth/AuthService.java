package com.filehome.auth;

import org.springframework.stereotype.Service;

import com.filehome.common.ApiException;
import com.filehome.user.UserAccount;
import com.filehome.user.UserService;
import com.filehome.user.UserView;

@Service
public class AuthService {

    private final UserService userService;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;

    public AuthService(
            UserService userService,
            PasswordHasher passwordHasher,
            JwtService jwtService) {
        this.userService = userService;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
    }

    public UserAccount authenticate(String account, String password) {
        UserAccount user = userService.requireByAccount(account);
        if (!user.enabled()
                || !passwordHasher.matches(password, user.passwordHash())) {
            throw ApiException.unauthorized(
                    "\u8d26\u6237\u6216\u5bc6\u7801\u9519\u8bef");
        }
        return user;
    }

    public UserAccount register(
            String username,
            String email,
            String password,
            String displayName) {
        return userService.register(username, email, password, displayName);
    }

    public AuthResponse cliResponse(UserAccount account) {
        String token = jwtService.issue(account.id(), account.username());
        return new AuthResponse(token, UserView.from(account));
    }

    public record AuthResponse(String accessToken, UserView user) {
    }
}
