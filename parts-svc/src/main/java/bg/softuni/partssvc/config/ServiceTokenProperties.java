package bg.softuni.partssvc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "parts.security.service-token")
public class ServiceTokenProperties {

    private String secret;

    private String issuer;
}
