package de.tum.cit.aet.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.*;
import tech.jhipster.config.cache.PrefixedKeyGenerator;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfiguration {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(20_000)
                .expireAfterAccess(Duration.ofMinutes(10))
                .recordStats()
        );
        return manager;
    }

    @Bean
    public KeyGenerator keyGenerator(GitProperties gitProperties, BuildProperties buildProperties) {
        return new PrefixedKeyGenerator(gitProperties, buildProperties);
    }
}
