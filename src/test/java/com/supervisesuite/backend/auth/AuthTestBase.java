package com.supervisesuite.backend.auth;

import com.supervisesuite.backend.auth.repository.RefreshTokenRepository;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projects.repository.ProjectMilestoneRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared test cleanup for auth integration tests.
 *
 * <p>Delete order is important to satisfy FK constraints:
 * project_milestones -> project_members -> projects -> refresh_tokens -> users.
 */
@ActiveProfiles("test")
public abstract class AuthTestBase {

    @Autowired
    protected ProjectMilestoneRepository projectMilestoneRepository;

    @Autowired
    protected ProjectMemberRepository projectMemberRepository;

    @Autowired
    protected ProjectRepository projectRepository;

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    protected UserRepository userRepository;

    protected void safeCleanup() {
        projectMilestoneRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }
}
