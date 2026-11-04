package com.filehome.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.filehome.config.AppProperties;

class SessionTokenServiceTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef";

    @Test
    void createsOpaqueTokensAndStableCsrfProofs() {
        SessionTokenService service = new SessionTokenService(SECRET);

        String first = service.newToken();
        String second = service.newToken();
        String csrf = service.csrfFor(first);

        assertThat(first).isNotEqualTo(second);
        assertThat(first).hasSize(43);
        assertThat(csrf).hasSize(43);
        assertThat(service.csrfFor(first)).isEqualTo(csrf);
        assertThat(service.csrfMatches(first, csrf)).isTrue();
        assertThat(service.csrfMatches(second, csrf)).isFalse();
        assertThat(service.hash(first)).hasSize(32);
    }

    @Test
    void sessionCookieIsHttpOnlyWhileCsrfCookieIsReadable() {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt(SECRET, 120),
                new AppProperties.Auth(12, false, 60),
                new AppProperties.Upload(
                        16 * 1024 * 1024,
                        1024L * 1024 * 1024,
                        48,
                        4),
                null,
                null,
                null,
                null);
        AuthCookieService cookies = new AuthCookieService(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SessionTokenService tokens = new SessionTokenService(SECRET);

        String sessionToken = tokens.newToken();
        cookies.write(
                response,
                sessionToken,
                tokens.csrfFor(sessionToken),
                java.time.Instant.now().plusSeconds(3600));

        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(value -> {
                    assertThat(value).contains("FH_SESSION=");
                    assertThat(value).contains("HttpOnly");
                    assertThat(value).contains("SameSite=Lax");
                })
                .anySatisfy(value -> {
                    assertThat(value).contains("FH_CSRF=");
                    assertThat(value).doesNotContain("HttpOnly");
                    assertThat(value).contains("SameSite=Lax");
                });
    }
}
