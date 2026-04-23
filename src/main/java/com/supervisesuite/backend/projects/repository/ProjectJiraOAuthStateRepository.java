package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectJiraOAuthState;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectJiraOAuthStateRepository extends JpaRepository<ProjectJiraOAuthState, UUID> {

    Optional<ProjectJiraOAuthState> findByStateNonceHash(String stateNonceHash);

    @Modifying
    @Query("""
        delete from ProjectJiraOAuthState state
        where state.expiresAt <= :now
    """)
    int deleteExpired(@Param("now") Instant now);
}
