package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectJiraOAuthState;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectJiraOAuthStateRepository extends JpaRepository<ProjectJiraOAuthState, UUID> {

    Optional<ProjectJiraOAuthState> findByStateNonceHash(String stateNonceHash);
}
