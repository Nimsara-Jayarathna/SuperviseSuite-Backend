package com.supervisesuite.backend.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.beans.factory.DisposableBean;

/**
 * Configures a dedicated thread pool for background tasks (Cron jobs).
 * This ensures that I/O-heavy background synchronization does not contend
 * with application web threads, providing resource isolation.
 */
@Configuration
public class SchedulerConfig implements SchedulingConfigurer, DisposableBean {

    private ThreadPoolTaskScheduler taskScheduler;
    private ThreadPoolTaskExecutor userSyncExecutor;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("background-sync-");
        scheduler.setErrorHandler(t -> {
            // Log unhandled exceptions in scheduled tasks
            org.slf4j.LoggerFactory.getLogger(SchedulerConfig.class)
                .error("Unhandled exception in scheduled task", t);
        });
        scheduler.initialize();

        this.taskScheduler = scheduler;
        taskRegistrar.setTaskScheduler(scheduler);
    }

    @Override
    public void destroy() throws Exception {
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
        if (userSyncExecutor != null) {
            userSyncExecutor.shutdown();
        }
    }

    @Bean(name = "userSyncExecutor")
    public Executor userSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("user-sync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        this.userSyncExecutor = executor;
        return executor;
    }
}
