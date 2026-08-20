package bg.softuni.partssvc.config;

import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(ServiceTokenProperties.class)
public class SecurityConfig {

    @Bean
    public SecretKey serviceTokenKey(ServiceTokenProperties properties) {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecretKey serviceTokenKey,
                                                   ServiceTokenProperties properties) throws Exception {
        ServiceTokenAuthenticationFilter tokenFilter =
                new ServiceTokenAuthenticationFilter(serviceTokenKey, properties.getIssuer());

        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(login -> login.disable())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/parts/**", "/api/suppliers/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/reservations/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/reservations").hasAuthority("PART_RESERVE")
                        .requestMatchers(HttpMethod.PUT, "/api/reservations/*/consume").hasAuthority("PART_RESERVE")
                        .requestMatchers(HttpMethod.DELETE, "/api/reservations/*").hasAuthority("PART_RESERVE")
                        .requestMatchers("/api/parts/*/restock").hasAuthority("PART_RESTOCK")
                        .requestMatchers(HttpMethod.POST, "/api/parts").hasAuthority("PART_RESTOCK")
                        .requestMatchers(HttpMethod.PUT, "/api/parts/*").hasAuthority("PART_RESTOCK")
                        .anyRequest().denyAll())
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
