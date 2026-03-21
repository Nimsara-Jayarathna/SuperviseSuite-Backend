package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectRepositoryContributor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepositoryContributorRepository extends JpaRepository<ProjectRepositoryContributor, UUID> {
    Optional<ProjectRepositoryContributor> findByRepositoryIdAndContributorName(UUID repositoryId, String contributorName);

    List<ProjectRepositoryContributor> findTop4ByRepositoryIdOrderByCommitCountDescContributorNameAsc(UUID repositoryId);

    Page<ProjectRepositoryContributor> findByRepositoryIdOrderByCommitCountDescContributorNameAsc(
        UUID repositoryId,
        Pageable pageable
    );

    void deleteByRepositoryId(UUID repositoryId);
}
