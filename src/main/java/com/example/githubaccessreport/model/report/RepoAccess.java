package com.example.githubaccessreport.model.report;

/**
 * One repository a user has access to, and the level of access they have.
 */
public record RepoAccess(
        String repository,
        String permission,
        String roleName,
        String repositoryUrl
) {
}
