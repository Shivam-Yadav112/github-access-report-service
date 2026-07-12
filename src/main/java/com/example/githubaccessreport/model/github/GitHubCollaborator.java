package com.example.githubaccessreport.model.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Subset of the fields GitHub returns from
 * GET /repos/{owner}/{repo}/collaborators.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCollaborator(
        String login,

        @JsonProperty("id")
        long id,

        /**
         * How the user got their access: "outside", "direct", "organization" (via team),
         * "team", etc. Requested via the `affiliation=all` query parameter.
         */
        @JsonProperty("permissions")
        Permissions permissions,

        @JsonProperty("role_name")
        String roleName,

        @JsonProperty("html_url")
        String htmlUrl
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Permissions(
            boolean pull,
            boolean triage,
            boolean push,
            boolean maintain,
            boolean admin
    ) {
        /**
         * Reduces GitHub's set of boolean permission flags to the single
         * highest privilege level, matching the role hierarchy GitHub itself uses.
         */
        public String highestPermission() {
            if (admin) return "admin";
            if (maintain) return "maintain";
            if (push) return "write";
            if (triage) return "triage";
            if (pull) return "read";
            return "none";
        }
    }
}
