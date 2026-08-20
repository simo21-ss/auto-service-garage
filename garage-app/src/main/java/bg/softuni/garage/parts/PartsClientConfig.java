package bg.softuni.garage.parts;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class PartsClientConfig {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";

    @Bean
    public RequestInterceptor serviceTokenInterceptor(ServiceTokenFactory tokenFactory) {
        return template -> template.header(AUTHORIZATION, BEARER + tokenFactory.issueToken());
    }

    @Bean
    public PartsErrorDecoder partsErrorDecoder() {
        return new PartsErrorDecoder();
    }
}
