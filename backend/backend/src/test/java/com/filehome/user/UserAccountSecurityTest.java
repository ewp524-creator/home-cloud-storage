package com.filehome.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.filehome.auth.PasswordHasher;
import com.filehome.common.ApiException;
import com.filehome.file.FileRepository;
import com.filehome.security.RequestUserContext;
import com.filehome.storage.ObjectStoragePort;

class UserAccountSecurityTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final UserPreferenceRepository preferences =
            mock(UserPreferenceRepository.class);
    private final ApplicationEventPublisher events =
            mock(ApplicationEventPublisher.class);
    private final UserService service = new UserService(
            userRepository,
            passwordHasher,
            mock(FileRepository.class),
            mock(ObjectStoragePort.class),
            mock(RequestUserContext.class),
            preferences,
            events);

    @Test
    void deactivationRequiresPasswordAndRevokesThroughEvent() {
        UserAccount account = account();
        when(userRepository.findByIdForUpdate(account.id()))
                .thenReturn(java.util.Optional.of(account));
        when(passwordHasher.matches("correct-password", "hash"))
                .thenReturn(true);

        service.deactivate(account.id(), "correct-password");

        verify(userRepository).deactivate(account.id());
        verify(events).publishEvent(new UserAccountDeactivatedEvent(account.id()));
    }

    @Test
    void deactivationRejectsWrongPassword() {
        UserAccount account = account();
        when(userRepository.findByIdForUpdate(account.id()))
                .thenReturn(java.util.Optional.of(account));
        when(passwordHasher.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.deactivate(account.id(), "wrong"))
                .isInstanceOf(ApiException.class);
    }

    private UserAccount account() {
        return new UserAccount(
                UUID.randomUUID(),
                "account-user",
                "account@example.com",
                "hash",
                "Account User",
                null,
                1024,
                true,
                Instant.now());
    }
}
