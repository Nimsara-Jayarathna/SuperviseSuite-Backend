package com.supervisesuite.backend.common.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnifiedSystemSyncSchedulerTest {

    @Mock
    private SystemSyncProvider provider1;

    @Mock
    private SystemSyncProvider provider2;

    @Test
    void executeAllSyncs_ShouldExecuteAllProviders() {
        UnifiedSystemSyncScheduler scheduler = new UnifiedSystemSyncScheduler(List.of(provider1, provider2));

        scheduler.executeAllSyncs();

        verify(provider1, times(1)).executeSync();
        verify(provider2, times(1)).executeSync();
    }

    @Test
    void executeAllSyncs_ShouldNotStopOnException() {
        doThrow(new RuntimeException("Test failure")).when(provider1).executeSync();

        UnifiedSystemSyncScheduler scheduler = new UnifiedSystemSyncScheduler(List.of(provider1, provider2));

        scheduler.executeAllSyncs();

        verify(provider1, times(1)).executeSync();
        verify(provider2, times(1)).executeSync(); // Should still be invoked
    }
}
