package com.example.githubaccessreport.model.report;

import java.util.List;

/**
 * A single user, aggregated across every repository in the organization
 * they have access to.
 */
public record UserAccessReport(
        String username,
        String profileUrl,
        int repositoryCount,
        List<RepoAccess> repositoryAccess
) {
}
