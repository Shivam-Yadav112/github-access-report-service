package com.example.githubaccessreport.model.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Subset of the fields GitHub returns from GET /orgs/{org}/repos.
 * We ignore everything we don't need.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepo(
        String name,

        @JsonProperty("full_name")
        String fullName,

        @JsonProperty("private")
        boolean isPrivate,

        String visibility,

        @JsonProperty("archived")
        boolean archived,

        @JsonProperty("html_url")
        String htmlUrl
) {
}
