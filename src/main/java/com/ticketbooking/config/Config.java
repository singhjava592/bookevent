package com.ticketbooking.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class Config {
  public static final String AVAILABILITY_CACHE = "availability";

  @Bean
  public CacheManager cacheManager(AppProperties props) {
    var ttl = Duration.ofSeconds(Math.max(1, props.cache().availabilityTtlSeconds()));
    var caffeine = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(50_000);
    var mgr = new CaffeineCacheManager(AVAILABILITY_CACHE);
    mgr.setCaffeine(caffeine);
    return mgr;
  }
}

