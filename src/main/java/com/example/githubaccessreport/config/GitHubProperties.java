package com.example.githubaccessreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Binds the `github.*` properties from application.yml (or environment variables,
 * e.g. GITHUB_TOKEN -> github.token).
 */
@Validated
@ConfigurationProperties(prefix = "github")
public record GitHubProperties(

        @NotBlank
        String apiBaseUrl,

        /**
         * A GitHub Personal Access Token (classic or fine-grained) or a GitHub App
         * installation token. Requires at least `read:org` and repo `admin`/collaborator
         * read scopes to list collaborators and their permission levels.
         */
        @NotBlank
        String token,

        /**
         * Number of items requested per page from GitHub's paginated endpoints.
         * GitHub's maximum is 100.
         */
        @Positive
        int pageSize,

        /**
         * Upper bound on how many collaborator-list requests are allowed to be
         * in flight at the same time. Keeps us well under GitHub's secondary
         * rate limits while still parallelizing across repositories.
         */
        @Positive
        int maxConcurrentRequests,

        /**
         * How long a generated report is cached before a request triggers a fresh pull.
         */
        @Positive
        long cacheTtlMinutes
) {
}
