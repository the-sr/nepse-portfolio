package com.nepse.portfolio.config;

import com.nepse.portfolio.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The phone-tunnel/LAN deployment serves the app from arbitrary origins
 * (192.168.x.x:3000, random *.trycloudflare.com). CORS must therefore accept
 * any origin while the JWT gate on writes remains unaffected.
 */
class SecurityConfigCorsTest {

    private CorsConfiguration configWith(String origins, String path) {
        SecurityConfig config = new SecurityConfig(null);
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);
        return config.corsConfigurationSource().getCorsConfiguration(new MockHttpServletRequest("GET", path));
    }

    @Test
    void wildcardAllowsTunnelAndLanOrigins() {
        CorsConfiguration config = configWith("*", "/api/auth/login");
        assertThat(config).isNotNull();
        // checkOrigin must echo the real origin (spec-safe with credentials on)
        assertThat(config.checkOrigin("https://random-tunnel-45c1.trycloudflare.com"))
                .isEqualTo("https://random-tunnel-45c1.trycloudflare.com");
        assertThat(config.checkOrigin("http://192.168.1.129:3000"))
                .isEqualTo("http://192.168.1.129:3000");
    }

    @Test
    void patternListStillNarrowableViaEnv() {
        CorsConfiguration config = configWith("http://localhost:3000,http://192.168.1.*:3000", "/api/market/quotes");
        assertThat(config).isNotNull();
        assertThat(config.checkOrigin("http://192.168.1.129:3000"))
                .isEqualTo("http://192.168.1.129:3000");
        assertThat(config.checkOrigin("https://evil.example.com")).isNull();
    }

    @Test
    void allowCredentialsStaysOn() {
        CorsConfiguration config = configWith("*", "/anything");
        assertThat(config.getAllowCredentials()).isTrue();
    }
}
