package bg.softuni.partssvc.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PART_CATALOGUE = "partCatalogue";
    public static final String LOW_STOCK = "lowStock";

    @Bean
    public RedisCacheConfiguration defaultCacheConfiguration() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                        ObjectMapper.DefaultTyping.EVERYTHING,
                        JsonTypeInfo.As.PROPERTY);

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)));
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheTtlCustomizer(
            RedisCacheConfiguration defaultCacheConfiguration) {
        return builder -> builder.withInitialCacheConfigurations(Map.of(
                PART_CATALOGUE, defaultCacheConfiguration.entryTtl(Duration.ofSeconds(90)),
                LOW_STOCK, defaultCacheConfiguration.entryTtl(Duration.ofSeconds(90))));
    }
}
