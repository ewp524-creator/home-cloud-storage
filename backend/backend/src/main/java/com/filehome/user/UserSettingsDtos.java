package com.filehome.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class UserSettingsDtos {

    private UserSettingsDtos() {
    }

    public record PreferenceView(
            String defaultView,
            String defaultSort,
            boolean reduceMotion,
            int uploadConcurrency) {
    }

    public record SettingsView(
            UserView profile,
            PreferenceView preferences) {
    }

    public record UpdatePreferencesRequest(
            @NotBlank String defaultView,
            @NotBlank String defaultSort,
            @NotNull Boolean reduceMotion,
            @NotNull Integer uploadConcurrency) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Email @Size(max = 254) String email) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword) {
    }

    public record DeactivateAccountRequest(
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Size(max = 32) String confirmation) {
    }
}
