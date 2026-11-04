package com.filehome.share;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.filehome.config.AppProperties;

@Component
public class ShareTokenService {

    private static final String ALPHABET =
            "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private final byte[] secret;

    public ShareTokenService(AppProperties properties) {
        String tokenSecret = properties.share().tokenSecret();
        if (tokenSecret == null || tokenSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "FILEHOME_SHARE_TOKEN_SECRET must contain at least 32 bytes");
        }
        this.secret = tokenSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String tokenFor(UUID shareId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(shareId.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder token = new StringBuilder(12);
            for (int index = 0; index < 12; index++) {
                int unsigned = digest[index] & 0xff;
                token.append(ALPHABET.charAt(unsigned % ALPHABET.length()));
            }
            return token.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot derive share token", exception);
        }
    }
}
