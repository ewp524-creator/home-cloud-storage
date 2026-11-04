package com.filehome.auth;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.filehome.common.ApiException;

@Service
public class CaptchaService {

    private static final String KEY_PREFIX = "filehome:auth:captcha:";
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 5;
    private static final int IMAGE_WIDTH = 156;
    private static final int IMAGE_HEIGHT = 52;
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public CaptchaService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Challenge issue() {
        String id = UUID.randomUUID().toString();
        String answer = randomCode();
        try {
            redis.opsForValue().set(key(id), answer, TTL);
        } catch (RuntimeException exception) {
            throw ApiException.serviceUnavailable(
                    "CAPTCHA_UNAVAILABLE",
                    "\u9a8c\u8bc1\u7801\u670d\u52a1\u6682\u4e0d\u53ef\u7528");
        }
        return new Challenge(
                id,
                renderImage(answer),
                Instant.now().plus(TTL));
    }

    public void verify(String captchaId, String answer) {
        if (captchaId == null || captchaId.isBlank()
                || answer == null || answer.isBlank()) {
            throw ApiException.badRequest(
                    "CAPTCHA_REQUIRED",
                    "\u8bf7\u8f93\u5165\u56fe\u5f62\u9a8c\u8bc1\u7801");
        }
        String stored;
        try {
            stored = redis.opsForValue().getAndDelete(key(captchaId.trim()));
        } catch (RuntimeException exception) {
            throw ApiException.serviceUnavailable(
                    "CAPTCHA_UNAVAILABLE",
                    "\u9a8c\u8bc1\u7801\u670d\u52a1\u6682\u4e0d\u53ef\u7528");
        }
        if (stored == null) {
            throw ApiException.badRequest(
                    "CAPTCHA_EXPIRED",
                    "\u9a8c\u8bc1\u7801\u5df2\u5931\u6548\uff0c\u8bf7\u5237\u65b0");
        }
        byte[] expected = stored
                .toUpperCase(Locale.ROOT)
                .getBytes(StandardCharsets.US_ASCII);
        byte[] provided = answer
                .trim()
                .toUpperCase(Locale.ROOT)
                .getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, provided)) {
            throw ApiException.badRequest(
                    "CAPTCHA_INVALID",
                    "\u56fe\u5f62\u9a8c\u8bc1\u7801\u9519\u8bef");
        }
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    private String renderImage(String answer) {
        BufferedImage image = new BufferedImage(
                IMAGE_WIDTH,
                IMAGE_HEIGHT,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(244, 247, 246));
            graphics.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
            drawNoise(graphics);
            drawCharacters(graphics, answer);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot render captcha", exception);
        }
    }

    private void drawNoise(Graphics2D graphics) {
        for (int index = 0; index < 8; index++) {
            graphics.setColor(new Color(
                    120 + RANDOM.nextInt(80),
                    130 + RANDOM.nextInt(70),
                    150 + RANDOM.nextInt(70)));
            graphics.drawLine(
                    RANDOM.nextInt(IMAGE_WIDTH),
                    RANDOM.nextInt(IMAGE_HEIGHT),
                    RANDOM.nextInt(IMAGE_WIDTH),
                    RANDOM.nextInt(IMAGE_HEIGHT));
        }
        for (int index = 0; index < 45; index++) {
            graphics.fillRect(
                    RANDOM.nextInt(IMAGE_WIDTH),
                    RANDOM.nextInt(IMAGE_HEIGHT),
                    2,
                    2);
        }
    }

    private void drawCharacters(Graphics2D graphics, String answer) {
        for (int index = 0; index < answer.length(); index++) {
            AffineTransform original = graphics.getTransform();
            double angle = Math.toRadians(-14 + RANDOM.nextInt(29));
            int x = 18 + index * 26;
            int y = 35 + RANDOM.nextInt(5);
            graphics.rotate(angle, x, y - 12);
            graphics.setFont(new Font(
                    Font.SANS_SERIF,
                    Font.BOLD,
                    25 + RANDOM.nextInt(4)));
            graphics.setColor(new Color(
                    30 + RANDOM.nextInt(45),
                    55 + RANDOM.nextInt(45),
                    65 + RANDOM.nextInt(45)));
            graphics.drawString(String.valueOf(answer.charAt(index)), x, y);
            graphics.setTransform(original);
        }
    }

    private String key(String id) {
        return KEY_PREFIX + id;
    }

    public record Challenge(
            String captchaId,
            String image,
            Instant expiresAt) {
    }
}
