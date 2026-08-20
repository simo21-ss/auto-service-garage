package bg.softuni.partssvc.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private static final String SCOPE_CLAIM = "scope";

    private final SecretKey signingKey;
    private final String expectedIssuer;

    public ServiceTokenAuthenticationFilter(SecretKey signingKey, String expectedIssuer) {
        this.signingKey = signingKey;
        this.expectedIssuer = expectedIssuer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (!StringUtils.hasText(header) || !header.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(expectedIssuer)
                    .build()
                    .parseSignedClaims(header.substring(PREFIX.length()))
                    .getPayload();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(claims.getSubject(), null, readAuthorities(claims));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException exception) {
            SecurityContextHolder.clearContext();
            log.warn("Rejected service token: {}", exception.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> readAuthorities(Claims claims) {
        String scope = claims.get(SCOPE_CLAIM, String.class);
        if (!StringUtils.hasText(scope)) {
            return List.of();
        }
        return Arrays.stream(scope.split(" "))
                .filter(StringUtils::hasText)
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
