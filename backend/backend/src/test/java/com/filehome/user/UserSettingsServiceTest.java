package com.filehome.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.filehome.common.ApiException;
import com.filehome.user.UserSettingsDtos.UpdatePreferencesRequest;

class UserSettingsServiceTest {

    private final UserService userService = mock(UserService.class);
    private final UserPreferenceRepository repository =
            mock(UserPreferenceRepository.class);
    private final UserSettingsService service =
            new UserSettingsService(userService, repository);

    @Test
    void returnsDefaultsAndPersistsValidatedPreferences() {
        UserAccount account = account();
        when(userService.require(account.id())).thenReturn(account);
        when(repository.findById(account.id())).thenReturn(Optional.empty());

        assertThat(service.get(account.id()).preferences().uploadConcurrency())
                .isEqualTo(4);

        UserSettingsDtos.SettingsView updated = service.update(
                account.id(),
                new UpdatePreferencesRequest("list", "name-asc", true, 2));

        assertThat(updated.preferences().defaultView()).isEqualTo("LIST");
        assertThat(updated.preferences().defaultSort()).isEqualTo("name-asc");
        assertThat(updated.preferences().reduceMotion()).isTrue();
        assertThat(updated.preferences().uploadConcurrency()).isEqualTo(2);
        verify(repository).upsert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsInvalidUploadConcurrency() {
        UserAccount account = account();
        when(userService.require(account.id())).thenReturn(account);

        assertThatThrownBy(() -> service.update(
                account.id(),
                new UpdatePreferencesRequest("GRID", "modified-desc", false, 8)))
                .isInstanceOf(ApiException.class);
    }

    private UserAccount account() {
        return new UserAccount(
                UUID.randomUUID(),
                "settings-user",
                "settings@example.com",
                "hash",
                "Settings User",
                null,
                1024,
                true,
                Instant.now());
    }
}
