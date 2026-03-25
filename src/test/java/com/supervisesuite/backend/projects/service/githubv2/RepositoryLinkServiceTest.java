package com.supervisesuite.backend.projects.service.githubv2;

import static org.mockito.Mockito.*;

import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLink;
import com.supervisesuite.backend.projects.integration.github.GitHubClient;
import com.supervisesuite.backend.projects.repository.GitHubAccessSourceRepository;
import com.supervisesuite.backend.projects.repository.GitHubRepositoryEntityRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkCommitRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkContributorRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RepositoryLinkServiceTest {

    @Mock
    private GitHubIntegrationGuardService guardService;
    @Mock
    private AccessSourceService accessSourceService;
    @Mock
    private GitHubAccessSourceRepository accessSourceRepository;
    @Mock
    private GitHubRepositoryEntityRepository gitHubRepositoryEntityRepository;
    @Mock
    private ProjectRepositoryLinkRepository projectRepositoryLinkRepository;
    @Mock
    private ProjectRepositoryLinkCommitRepository projectRepositoryLinkCommitRepository;
    @Mock
    private ProjectRepositoryLinkContributorRepository projectRepositoryLinkContributorRepository;
    @Mock
    private GitHubSyncService gitHubSyncService;
    @Mock
    private GitHubClient gitHubClient;
    @Mock
    private GitHubProperties gitHubProperties;

    @InjectMocks
    private RepositoryLinkService repositoryLinkService;

    private UUID projectId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void selectPrimaryRepository_demotesCurrentPrimaryBeforePromotingNew() {
        UUID oldPrimaryId = UUID.randomUUID();
        UUID newPrimaryId = UUID.randomUUID();

        ProjectRepositoryLink oldPrimary = new ProjectRepositoryLink();
        oldPrimary.setId(oldPrimaryId);
        oldPrimary.setProjectId(projectId);
        oldPrimary.setIsPrimary(true);
        oldPrimary.setIsEnabled(true);

        ProjectRepositoryLink newPrimary = new ProjectRepositoryLink();
        newPrimary.setId(newPrimaryId);
        newPrimary.setProjectId(projectId);
        newPrimary.setIsPrimary(false);
        newPrimary.setIsEnabled(true);

        when(guardService.parseUuid(newPrimaryId.toString(), "repositoryId")).thenReturn(newPrimaryId);
        when(guardService.parseUuid(userId.toString(), "authenticatedUserId")).thenReturn(userId);
        when(guardService.parseUuid(projectId.toString(), "projectId")).thenReturn(projectId);
        
        when(projectRepositoryLinkRepository.findById(newPrimaryId)).thenReturn(Optional.of(newPrimary));
        when(projectRepositoryLinkRepository.findByProjectIdAndIsPrimaryTrue(projectId)).thenReturn(Optional.of(oldPrimary));

        when(accessSourceService.getProjectAccessSources(projectId)).thenReturn(List.of());
        when(projectRepositoryLinkRepository.findByProjectIdOrderByLinkedAtDesc(projectId)).thenReturn(List.of(oldPrimary, newPrimary));

        repositoryLinkService.selectPrimaryRepository(newPrimaryId.toString(), userId.toString());

        org.mockito.InOrder inOrder = inOrder(projectRepositoryLinkRepository);
        
        // Verify demotion happens first
        inOrder.verify(projectRepositoryLinkRepository).save(oldPrimary);
        inOrder.verify(projectRepositoryLinkRepository).flush();

        // Verify promotion happens after flush
        inOrder.verify(projectRepositoryLinkRepository).save(newPrimary);
        
        assert !oldPrimary.getIsPrimary();
        assert newPrimary.getIsPrimary();
    }
}
