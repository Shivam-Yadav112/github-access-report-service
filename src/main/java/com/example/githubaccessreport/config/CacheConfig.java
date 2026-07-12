package com.example.githubaccessreport.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.example.githubaccessreport.model.report.AccessReportResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    /**
     * Caches one generated {@link AccessReportResponse} per organization name.
     * Building a report requires 1 (paginated) call per repo just to list
     * collaborators, so for a 100+ repo org we avoid repeating that work on
     * every incoming HTTP request.
     */
    @Bean
    public Cache<String, AccessReportResponse> accessReportCache(GitHubProperties properties) {
        return Caffeine.newBuilder()
                .expireAfterWrite(properties.cacheTtlMinutes(), TimeUnit.MINUTES)
                .maximumSize(50)
                .build();
    }
}
