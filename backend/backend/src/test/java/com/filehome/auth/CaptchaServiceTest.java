package com.filehome.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.filehome.common.ApiException;

class CaptchaServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private CaptchaService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service = new CaptchaService(redis);
    }

    @Test
    void issuesOneTimeImageChallenge() {
        CaptchaService.Challenge challenge = service.issue();

        assertThat(challenge.captchaId()).isNotBlank();
        assertThat(challenge.image()).startsWith("data:image/png;base64,");
        assertThat(challenge.expiresAt()).isNotNull();
        verify(values).set(
                anyString(),
                anyString(),
                eq(Duration.ofMinutes(2)));
    }

    @Test
    void acceptsCaseInsensitiveAnswer() {
        when(values.getAndDelete(anyString())).thenReturn("ABCDE");

        assertThatCode(() -> service.verify("captcha-id", "abcde"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWrongAnswerAfterRemovingChallenge() {
        when(values.getAndDelete(anyString())).thenReturn("ABCDE");

        assertThatThrownBy(() -> service.verify("captcha-id", "ZZZZZ"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("CAPTCHA_INVALID"));
    }

    @Test
    void rejectsMissingOrExpiredChallenge() {
        when(values.getAndDelete(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.verify("captcha-id", "ABCDE"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("CAPTCHA_EXPIRED"));
    }
}
