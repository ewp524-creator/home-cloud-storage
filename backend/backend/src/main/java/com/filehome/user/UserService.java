package com.filehome.user;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filehome.auth.PasswordHasher;
import com.filehome.common.ApiException;
import com.filehome.file.FileRepository;
import com.filehome.security.RequestUserContext;
import com.filehome.storage.ObjectStoragePort;

@Service
public class UserService {

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[\\p{L}\\p{N}_-]{2,32}$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final long DEFAULT_QUOTA = 20L * 1024 * 1024 * 1024;

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final FileRepository fileRepository;
    private final ObjectStoragePort objectStorage;
    private final RequestUserContext requestUserContext;
    private final UserPreferenceRepository preferenceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            FileRepository fileRepository,
            ObjectStoragePort objectStorage,
            RequestUserContext requestUserContext,
            UserPreferenceRepository preferenceRepository,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.fileRepository = fileRepository;
        this.objectStorage = objectStorage;
        this.requestUserContext = requestUserContext;
        this.preferenceRepository = preferenceRepository;
        this.eventPublisher = eventPublisher;
    }

    public UserAccount require(UUID userId) {
        return requestUserContext.current(userId)
                .or(() -> userRepository.findById(userId))
                .orElseThrow(() -> ApiException.notFound(
                        "\u7528\u6237\u4e0d\u5b58\u5728"));
    }

    public UserAccount requireEnabled(UUID userId) {
        UserAccount account = require(userId);
        if (!account.enabled()) {
            throw ApiException.unauthorized(
                    "\u8d26\u6237\u5df2\u505c\u7528");
        }
        return account;
    }

    public UserAccount requireForUpdate(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> ApiException.notFound(
                        "\u7528\u6237\u4e0d\u5b58\u5728"));
    }

    public UserAccount requireByAccount(String account) {
        return userRepository.findByAccount(account.trim())
                .orElseThrow(() -> ApiException.unauthorized(
                        "\u8d26\u6237\u6216\u5bc6\u7801\u9519\u8bef"));
    }

    @Transactional
    public UserAccount register(
            String username,
            String email,
            String password,
            String displayName) {
        String cleanUsername = normalizeUsername(username);
        String cleanEmail = normalizeEmail(email);
        String cleanDisplayName = normalizeDisplayName(
                displayName,
                cleanUsername);
        validatePassword(password);
        if (userRepository.findByUsername(cleanUsername).isPresent()) {
            throw ApiException.conflict(
                    "USERNAME_EXISTS",
                    "\u7528\u6237\u540d\u5df2\u5b58\u5728\uff0c\u4e0d\u80fd\u91cd\u540d");
        }
        if (userRepository.findByEmail(cleanEmail).isPresent()) {
            throw ApiException.conflict(
                    "EMAIL_EXISTS",
                    "\u90ae\u7bb1\u5df2\u88ab\u6ce8\u518c");
        }

        UserAccount account = new UserAccount(
                UUID.randomUUID(),
                cleanUsername,
                cleanEmail,
                passwordHasher.hash(password),
                cleanDisplayName,
                null,
                DEFAULT_QUOTA,
                true,
                Instant.now());
        userRepository.insert(account);
        preferenceRepository.upsert(UserPreference.defaults(account.id()));
        fileRepository.createRoot(account.id(), cleanUsername);
        objectStorage.ensureUserRoot(cleanUsername);
        return account;
    }

    @Transactional
    public UserAccount updateProfile(
            UUID userId,
            String email,
            String displayName) {
        UserAccount account = requireForUpdate(userId);
        String cleanEmail = normalizeEmail(email);
        String cleanDisplayName = normalizeDisplayName(
                displayName,
                account.username());
        userRepository.findByEmail(cleanEmail)
                .filter(existing -> !existing.id().equals(userId))
                .ifPresent(existing -> {
                    throw ApiException.conflict(
                            "EMAIL_EXISTS",
                            "\u90ae\u7bb1\u5df2\u88ab\u6ce8\u518c");
                });
        userRepository.updateProfile(userId, cleanEmail, cleanDisplayName);
        eventPublisher.publishEvent(new UserProfileChangedEvent(userId));
        return new UserAccount(
                account.id(),
                account.username(),
                cleanEmail,
                account.passwordHash(),
                cleanDisplayName,
                account.avatarUrl(),
                account.storageQuotaBytes(),
                account.enabled(),
                account.createdAt());
    }

    @Transactional
    public void changePassword(
            UUID userId,
            String currentPassword,
            String newPassword) {
        UserAccount account = requireForUpdate(userId);
        if (!passwordHasher.matches(currentPassword, account.passwordHash())) {
            throw ApiException.unauthorized(
                    "\u5f53\u524d\u5bc6\u7801\u4e0d\u6b63\u786e");
        }
        validatePassword(newPassword);
        userRepository.updatePasswordHash(
                userId,
                passwordHasher.hash(newPassword));
    }

    @Transactional
    public void deactivate(UUID userId, String password) {
        UserAccount account = requireForUpdate(userId);
        if (!passwordHasher.matches(password, account.passwordHash())) {
            throw ApiException.unauthorized(
                    "\u5bc6\u7801\u4e0d\u6b63\u786e\uff0c\u65e0\u6cd5\u6ce8\u9500\u8d26\u6237");
        }
        userRepository.deactivate(userId);
        eventPublisher.publishEvent(new UserAccountDeactivatedEvent(userId));
    }

    private String normalizeUsername(String username) {
        String value = username == null ? "" : username.trim();
        if (!USERNAME_PATTERN.matcher(value).matches()) {
            throw ApiException.badRequest(
                    "INVALID_USERNAME",
                    "\u7528\u6237\u540d\u53ea\u80fd\u5305\u542b\u6587\u5b57\u3001\u6570\u5b57\u3001\u4e0b\u5212\u7ebf\u6216\u77ed\u6a2a\u7ebf\uff0c\u957f\u5ea6 2-32");
        }
        return value;
    }

    private String normalizeEmail(String email) {
        String value = email == null ? "" : email.trim().toLowerCase();
        if (value.length() > 254 || !EMAIL_PATTERN.matcher(value).matches()) {
            throw ApiException.badRequest(
                    "INVALID_EMAIL",
                    "\u90ae\u7bb1\u683c\u5f0f\u4e0d\u6b63\u786e");
        }
        return value;
    }

    private String normalizeDisplayName(String displayName, String fallback) {
        String value = displayName == null || displayName.isBlank()
                ? fallback
                : displayName.trim();
        if (value.length() > 80
                || value.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest(
                    "INVALID_DISPLAY_NAME",
                    "\u663e\u793a\u540d\u79f0\u4e0d\u5408\u6cd5");
        }
        return value;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw ApiException.badRequest(
                    "INVALID_PASSWORD",
                    "\u5bc6\u7801\u957f\u5ea6\u9700\u8981\u5728 8-128 \u4f4d\u4e4b\u95f4");
        }
    }
}
