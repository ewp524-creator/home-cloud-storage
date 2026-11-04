package com.filehome.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import com.filehome.common.ApiException;
import com.filehome.storage.ObjectStoragePort;

class UserAvatarServiceTest {

    private final UserService userService = mock(UserService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ObjectStoragePort objectStorage = mock(ObjectStoragePort.class);
    private final ApplicationEventPublisher eventPublisher =
            mock(ApplicationEventPublisher.class);
    private final UserAvatarService service = new UserAvatarService(
            userService,
            userRepository,
            objectStorage,
            eventPublisher);

    @Test
    void normalizesAvatarAndPersistsVersionedUrl() throws IOException {
        UserAccount account = account();
        when(userService.require(account.id())).thenReturn(account);
        when(objectStorage.bucketName()).thenReturn("file-home");
        MockMultipartFile file = new MockMultipartFile(
                "avatar",
                "portrait.png",
                "image/png",
                png(800, 400));

        UserView view = service.update(account.id(), file);

        assertThat(view.avatarUrl())
                .startsWith("/api/v1/users/me/avatar?v=");
        verify(objectStorage).putObject(
                eq("avatars/" + account.id() + "/avatar"),
                anyLong(),
                eq("image/png"),
                any());
        verify(userRepository).updateAvatarUrl(
                eq(account.id()),
                anyString());
    }

    @Test
    void rejectsContentThatIsNotAnImage() {
        UserAccount account = account();
        when(userService.require(account.id())).thenReturn(account);
        MockMultipartFile file = new MockMultipartFile(
                "avatar",
                "not-an-image.txt",
                "text/plain",
                "hello".getBytes());

        assertThatThrownBy(() -> service.update(account.id(), file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("\u6709\u6548");
    }

    private UserAccount account() {
        return new UserAccount(
                UUID.randomUUID(),
                "avatar-user",
                "avatar@example.com",
                "password-hash",
                "Avatar User",
                null,
                1024,
                true,
                Instant.now());
    }

    private byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
