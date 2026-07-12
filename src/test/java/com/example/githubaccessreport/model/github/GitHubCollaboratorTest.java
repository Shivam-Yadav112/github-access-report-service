package com.example.githubaccessreport.model.github;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubCollaboratorTest {

    @Test
    void adminTakesPriorityOverEverythingElse() {
        var permissions = new GitHubCollaborator.Permissions(true, true, true, true, true);
        assertThat(permissions.highestPermission()).isEqualTo("admin");
    }

    @Test
    void maintainWinsWhenAdminIsFalse() {
        var permissions = new GitHubCollaborator.Permissions(true, true, true, true, false);
        assertThat(permissions.highestPermission()).isEqualTo("maintain");
    }

    @Test
    void writeWinsWhenOnlyPushIsTrue() {
        var permissions = new GitHubCollaborator.Permissions(true, false, true, false, false);
        assertThat(permissions.highestPermission()).isEqualTo("write");
    }

    @Test
    void readOnlyPull() {
        var permissions = new GitHubCollaborator.Permissions(true, false, false, false, false);
        assertThat(permissions.highestPermission()).isEqualTo("read");
    }

    @Test
    void noneWhenEverythingFalse() {
        var permissions = new GitHubCollaborator.Permissions(false, false, false, false, false);
        assertThat(permissions.highestPermission()).isEqualTo("none");
    }
}
