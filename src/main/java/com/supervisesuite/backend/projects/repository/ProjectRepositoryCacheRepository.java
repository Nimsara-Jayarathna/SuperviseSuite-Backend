package com.supervisesuite.backend.projects.repository;

import com.supervisesuite.backend.projects.entity.ProjectRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepositoryCacheRepository extends JpaRepository<ProjectRepository, UUID> {
    Optional<ProjectRepository> findByProjectIdAndIsPrimaryTrue(UUID projectId);

    Optional<ProjectRepository> findByProjectIdAndProviderAndRepositoryUrl(
        UUID projectId,
        String provider,
        String repositoryUrl
    );

    List<ProjectRepository> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    List<ProjectRepository> findByInstallationId(Long installationId);

    Page<ProjectRepository> findByProviderIgnoreCaseAndIsPrimaryTrueAndRepositoryUrlIsNotNull(
        String provider,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProjectRepository> findById(UUID id);
}
