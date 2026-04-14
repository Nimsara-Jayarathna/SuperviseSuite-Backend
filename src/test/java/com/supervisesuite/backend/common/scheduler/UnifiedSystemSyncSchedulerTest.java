package com.supervisesuite.backend.common.scheduler;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UnifiedSystemSyncSchedulerTest {

    @Test
    void executeAllSyncs_skipsNestedRunWhenAlreadyRunning() {
        SystemSyncProvider provider = Mockito.mock(SystemSyncProvider.class);
        UnifiedSystemSyncScheduler scheduler = new UnifiedSystemSyncScheduler(List.of(provider));

        Mockito.doAnswer(invocation -> {
            scheduler.executeAllSyncs();
            return null;
        }).when(provider).executeSync();

        scheduler.executeAllSyncs();

        verify(provider, times(1)).executeSync();
    }
}
