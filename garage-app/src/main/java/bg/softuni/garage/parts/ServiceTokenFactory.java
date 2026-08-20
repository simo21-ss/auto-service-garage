package bg.softuni.garage.parts;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@Component
public class ServiceTokenFactory {

    private static final String SCOPE_CLAIM = "scope";
    private static final String SYSTEM_SUBJECT = "system";

    private final SecretKey signingKey;
    private final PartsServiceProperties properties;

    public ServiceTokenFactory(PartsServiceProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(
                properties.getServiceToken().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(subjectOf(authentication))
                .issuer(properties.getServiceToken().getIssuer())
                .claim(SCOPE_CLAIM, scopeOf(authentication))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getServiceToken().getTtlSeconds())))
                .signWith(signingKey)
                .compact();
    }

    private String subjectOf(Authentication authentication) {
        return authentication == null ? SYSTEM_SUBJECT : authentication.getName();
    }

    private String scopeOf(Authentication authentication) {
        if (authentication == null) {
            return "PART_RESERVE PART_RESTOCK";
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> !authority.startsWith("ROLE_"))
                .collect(Collectors.joining(" "));
    }
}
