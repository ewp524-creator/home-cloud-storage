package com.filehome.user;

import java.time.Duration;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.filehome.file.FileService;
import com.filehome.auth.AuthInterceptor;
import com.filehome.auth.session.AuthSessionService;
import com.filehome.common.ApiException;
import com.filehome.user.UserSettingsDtos.ChangePasswordRequest;
import com.filehome.user.UserSettingsDtos.DeactivateAccountRequest;
import com.filehome.user.UserSettingsDtos.SettingsView;
import com.filehome.user.UserSettingsDtos.UpdatePreferencesRequest;
import com.filehome.user.UserSettingsDtos.UpdateProfileRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final UserAvatarService avatarService;
    private final UserSettingsService settingsService;
    private final AuthSessionService sessionService;
    private final FileService fileService;

    public UserController(
            UserService userService,
            UserAvatarService avatarService,
            UserSettingsService settingsService,
            AuthSessionService sessionService,
            FileService fileService) {
        this.userService = userService;
        this.avatarService = avatarService;
        this.settingsService = settingsService;
        this.sessionService = sessionService;
        this.fileService = fileService;
    }

    @GetMapping("/me")
    public UserView me(@RequestAttribute("auth.userId") UUID userId) {
        return UserView.from(userService.require(userId));
    }

    @GetMapping("/me/settings")
    public SettingsView settings(
            @RequestAttribute("auth.userId") UUID userId) {
        return settingsService.get(userId);
    }

    @PatchMapping("/me/settings")
    public SettingsView updateSettings(
            @RequestAttribute("auth.userId") UUID userId,
            @Valid @RequestBody UpdatePreferencesRequest request) {
        return settingsService.update(userId, request);
    }

    @PatchMapping("/me/profile")
    public UserView updateProfile(
            @RequestAttribute("auth.userId") UUID userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return UserView.from(userService.updateProfile(
                userId,
                request.email(),
                request.displayName()));
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @RequestAttribute("auth.userId") UUID userId,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest servletRequest) {
        userService.changePassword(
                userId,
                request.currentPassword(),
                request.newPassword());
        UUID currentSessionId = (UUID) servletRequest.getAttribute(
                AuthInterceptor.SESSION_ID_ATTRIBUTE);
        if (currentSessionId != null) {
            sessionService.revokeOthers(userId, currentSessionId);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/deactivate")
    public ResponseEntity<Void> deactivate(
            @RequestAttribute("auth.userId") UUID userId,
            @Valid @RequestBody DeactivateAccountRequest request,
            HttpServletResponse response) {
        UserAccount account = userService.require(userId);
        if (!account.username().equals(request.confirmation().trim())) {
            throw ApiException.badRequest(
                    "CONFIRMATION_MISMATCH",
                    "\u8bf7\u8f93\u5165\u5f53\u524d\u7528\u6237\u540d\u786e\u8ba4\u6ce8\u9500");
        }
        userService.deactivate(userId, request.password());
        sessionService.clearCookies(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(
            value = "/me/avatar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserView uploadAvatar(
            @RequestAttribute("auth.userId") UUID userId,
            @RequestPart("avatar") MultipartFile avatar) {
        return avatarService.update(userId, avatar);
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<Void> removeAvatar(
            @RequestAttribute("auth.userId") UUID userId) {
        avatarService.remove(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/avatar")
    public ResponseEntity<InputStreamResource> avatar(
            @RequestAttribute("auth.userId") UUID userId) {
        UserAvatarService.AvatarContent avatar = avatarService.open(userId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl
                        .maxAge(Duration.ofHours(1))
                        .cachePrivate())
                .eTag(avatar.etag())
                .contentType(MediaType.IMAGE_PNG)
                .body(new InputStreamResource(avatar.inputStream()));
    }

    @GetMapping("/me/storage")
    public StorageUsage storage(@RequestAttribute("auth.userId") UUID userId) {
        UserAccount user = userService.require(userId);
        var stats = fileService.storageStats(userId);
        return new StorageUsage(
                stats.get("usedBytes"),
                user.storageQuotaBytes(),
                stats.get("fileCount"),
                stats.get("folderCount"));
    }

    public record StorageUsage(
            long usedBytes,
            long quotaBytes,
            long fileCount,
            long folderCount) {
    }
}
