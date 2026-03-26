package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.GitHubSetupState;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GitHubSetupStateRepository extends JpaRepository<GitHubSetupState, UUID> {
    Optional<GitHubSetupState> findByStateJtiHash(String stateJtiHash);

    @Modifying
    @Query("""
        delete from GitHubSetupState state
        where state.expiresAt <= :now
    """)
    int deleteExpired(@Param("now") Instant now);
}
