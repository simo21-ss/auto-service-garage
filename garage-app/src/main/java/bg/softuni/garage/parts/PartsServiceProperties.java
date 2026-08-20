package bg.softuni.garage.parts;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "garage.security")
public class PartsServiceProperties {

    private ServiceToken serviceToken = new ServiceToken();

    @Getter
    @Setter
    public static class ServiceToken {

        private String secret;

        private String issuer;

        private long ttlSeconds;
    }
}
