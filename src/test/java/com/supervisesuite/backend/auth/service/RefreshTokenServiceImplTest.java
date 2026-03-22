package com.supervisesuite.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.auth.entity.RefreshToken;
import com.supervisesuite.backend.auth.repository.RefreshTokenRepository;
import com.supervisesuite.backend.config.JwtProperties;
import com.supervisesuite.backend.users.entity.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setRefreshTokenExpirySeconds(3600);
        service = new RefreshTokenServiceImpl(refreshTokenRepository, jwtProperties);

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("student@university.ac.lk");
    }

    @Test
    void issue_generatesRawTokenAndStoresOnlyHash() {
        when(refreshTokenRepository.save(any(RefreshToken.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = service.issue(user);

        assertThat(rawToken).isNotBlank();

        verify(refreshTokenRepository).save(org.mockito.ArgumentMatchers.argThat(token ->
            token.getUser().equals(user)
                && token.getTokenHash() != null
                && !rawToken.equals(token.getTokenHash())
                && token.getExpiresAt().isAfter(Instant.now())
                && token.getCreatedAt() != null
        ));
    }

    @Test
    void revoke_existingToken_setsRevokedAtAndSaves() {
        String rawToken = "known-token";
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUser(user);
        token.setCreatedAt(Instant.now().minusSeconds(30));
        token.setExpiresAt(Instant.now().plusSeconds(300));

        when(refreshTokenRepository.findByTokenHash(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.revoke(rawToken);

        assertThat(token.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void revoke_unknownToken_doesNothing() {
        when(refreshTokenRepository.findByTokenHash(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(Optional.empty());

        service.revoke("unknown-token");

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }
}
