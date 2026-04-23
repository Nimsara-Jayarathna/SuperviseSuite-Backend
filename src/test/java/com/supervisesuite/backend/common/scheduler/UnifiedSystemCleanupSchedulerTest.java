package com.supervisesuite.backend.common.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnifiedSystemCleanupSchedulerTest {

    @Mock
    private SystemCleanupProvider provider1;

    @Mock
    private SystemCleanupProvider provider2;

    @Test
    void executeAllCleanups_ShouldExecuteAllProviders() {
        UnifiedSystemCleanupScheduler scheduler = new UnifiedSystemCleanupScheduler(List.of(provider1, provider2));

        scheduler.executeAllCleanups();

        verify(provider1, times(1)).executeCleanup();
        verify(provider2, times(1)).executeCleanup();
    }

    @Test
    void executeAllCleanups_ShouldNotStopOnException() {
        doThrow(new RuntimeException("Test failure")).when(provider1).executeCleanup();

        UnifiedSystemCleanupScheduler scheduler = new UnifiedSystemCleanupScheduler(List.of(provider1, provider2));

        scheduler.executeAllCleanups();

        verify(provider1, times(1)).executeCleanup();
        verify(provider2, times(1)).executeCleanup(); // Should still be invoked
    }
}
