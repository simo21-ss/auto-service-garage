package bg.softuni.garage.common;

import bg.softuni.garage.parts.PartsServiceProperties;
import bg.softuni.garage.parts.ServiceTokenFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTokenFactoryTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha-256-signing";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aTokenCarriesTheActingUsersPermissionsAsScope() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("mechanic", null,
                        List.of(new SimpleGrantedAuthority("ROLE_MECHANIC"),
                                new SimpleGrantedAuthority("PART_RESERVE"))));

        Claims claims = parse(factory().issueToken());

        assertThat(claims.getSubject()).isEqualTo("mechanic");
        assertThat(claims.getIssuer()).isEqualTo("garage-app");
        assertThat(claims.get("scope", String.class)).isEqualTo("PART_RESERVE");
    }

    @Test
    void roleAuthoritiesAreNotLeakedIntoTheScope() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThat(parse(factory().issueToken()).get("scope", String.class)).isEmpty();
    }

    @Test
    void aBackgroundJobIssuesASystemToken() {
        Claims claims = parse(factory().issueToken());

        assertThat(claims.getSubject()).isEqualTo("system");
        assertThat(claims.get("scope", String.class)).contains("PART_RESERVE", "PART_RESTOCK");
    }

    @Test
    void tokensCarryAnExpiry() {
        Claims claims = parse(factory().issueToken());

        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    private ServiceTokenFactory factory() {
        PartsServiceProperties properties = new PartsServiceProperties();
        properties.getServiceToken().setSecret(SECRET);
        properties.getServiceToken().setIssuer("garage-app");
        properties.getServiceToken().setTtlSeconds(60);
        return new ServiceTokenFactory(properties);
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
