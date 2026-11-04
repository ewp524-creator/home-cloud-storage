package com.filehome.user;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Iterator;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;

import com.filehome.common.ApiException;
import com.filehome.storage.ObjectStoragePort;

@Service
public class UserAvatarService {

    private static final long MAX_SOURCE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_SOURCE_PIXELS = 36_000_000;
    private static final int MAX_SOURCE_EDGE = 12_000;
    private static final int AVATAR_SIZE = 512;

    private final UserService userService;
    private final UserRepository userRepository;
    private final ObjectStoragePort objectStorage;
    private final ApplicationEventPublisher eventPublisher;

    public UserAvatarService(
            UserService userService,
            UserRepository userRepository,
            ObjectStoragePort objectStorage,
            ApplicationEventPublisher eventPublisher) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.objectStorage = objectStorage;
        this.eventPublisher = eventPublisher;
    }

    public UserView update(UUID userId, MultipartFile avatar) {
        UserAccount account = userService.require(userId);
        byte[] normalized = normalize(avatar);
        objectStorage.putObject(
                objectKey(userId),
                normalized.length,
                "image/png",
                new ByteArrayInputStream(normalized));
        String avatarUrl = "/api/v1/users/me/avatar?v="
                + Instant.now().toEpochMilli();
        userRepository.updateAvatarUrl(userId, avatarUrl);
        eventPublisher.publishEvent(new UserProfileChangedEvent(userId));
        return UserView.from(withAvatarUrl(account, avatarUrl));
    }

    public void remove(UUID userId) {
        userService.require(userId);
        userRepository.updateAvatarUrl(userId, null);
        eventPublisher.publishEvent(new UserProfileChangedEvent(userId));
        objectStorage.delete(objectStorage.bucketName(), objectKey(userId));
    }

    public AvatarContent open(UUID userId) {
        UserAccount account = userService.require(userId);
        if (account.avatarUrl() == null || account.avatarUrl().isBlank()) {
            throw ApiException.notFound("\u5934\u50cf\u5c1a\u672a\u8bbe\u7f6e");
        }
        String etag = account.avatarUrl().replaceAll(".*[?&]v=", "");
        return new AvatarContent(
                objectStorage.get(
                        objectStorage.bucketName(),
                        objectKey(userId)),
                etag);
    }

    private byte[] normalize(MultipartFile avatar) {
        if (avatar == null || avatar.isEmpty()) {
            throw ApiException.badRequest(
                    "EMPTY_AVATAR",
                    "\u8bf7\u9009\u62e9\u5934\u50cf\u56fe\u7247");
        }
        if (avatar.getSize() > MAX_SOURCE_BYTES) {
            throw ApiException.badRequest(
                    "AVATAR_TOO_LARGE",
                    "\u5934\u50cf\u6e90\u6587\u4ef6\u4e0d\u80fd\u8d85\u8fc7 5 MB");
        }
        try {
            BufferedImage image = readBoundedImage(avatar.getBytes());
            BufferedImage output = renderSquare(image);
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            if (!ImageIO.write(output, "png", result)) {
                throw invalidAvatar();
            }
            return result.toByteArray();
        } catch (IOException exception) {
            throw invalidAvatar();
        }
    }

    private BufferedImage readBoundedImage(byte[] source) throws IOException {
        try (ImageInputStream input =
                ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (input == null) {
                throw invalidAvatar();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidAvatar();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1
                        || height < 1
                        || width > MAX_SOURCE_EDGE
                        || height > MAX_SOURCE_EDGE
                        || (long) width * height > MAX_SOURCE_PIXELS) {
                    throw ApiException.badRequest(
                            "AVATAR_DIMENSIONS",
                            "\u5934\u50cf\u56fe\u7247\u5c3a\u5bf8\u8fc7\u5927");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private BufferedImage renderSquare(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int sourceX = (source.getWidth() - side) / 2;
        int sourceY = (source.getHeight() - side) / 2;
        BufferedImage output = new BufferedImage(
                AVATAR_SIZE,
                AVATAR_SIZE,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(
                    source,
                    0,
                    0,
                    AVATAR_SIZE,
                    AVATAR_SIZE,
                    sourceX,
                    sourceY,
                    sourceX + side,
                    sourceY + side,
                    null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private UserAccount withAvatarUrl(UserAccount account, String avatarUrl) {
        return new UserAccount(
                account.id(),
                account.username(),
                account.email(),
                account.passwordHash(),
                account.displayName(),
                avatarUrl,
                account.storageQuotaBytes(),
                account.enabled(),
                account.createdAt());
    }

    private ApiException invalidAvatar() {
        return ApiException.badRequest(
                "INVALID_AVATAR",
                "\u8bf7\u9009\u62e9\u6709\u6548\u7684 PNG\u3001JPEG \u6216 GIF \u56fe\u7247");
    }

    private String objectKey(UUID userId) {
        return "avatars/" + userId + "/avatar";
    }

    public record AvatarContent(InputStream inputStream, String etag) {
    }
}
