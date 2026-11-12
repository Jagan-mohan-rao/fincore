package com.fincore.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    @Test void registrationIssuesAnHs256TokenWithCustomerClaims() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("SELECT COUNT(*) FROM customers WHERE email = ?"), eq(Integer.class), eq("demo@example.com"))).thenReturn(0);
        when(jdbc.queryForObject(eq("SELECT id FROM customers WHERE email = ?"), eq(Long.class), eq("demo@example.com"))).thenReturn(41L);
        SecretKey key = new SecretKeySpec(new byte[32], "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        AuthService service = new AuthService(jdbc, new BCryptPasswordEncoder(), encoder, Duration.ofHours(8));

        Map<String,Object> session = service.register("Demo Customer", "DEMO@example.com", "password123");
        var token = decoder.decode((String) session.get("token"));

        assertEquals("41", token.getSubject());
        assertEquals("Demo Customer", token.getClaimAsString("name"));
        assertEquals("demo@example.com", token.getClaimAsString("email"));
        assertEquals("https://fincore.local", token.getIssuer().toString());
    }
}
