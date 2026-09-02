package com.aiclassrecovery;

import java.util.concurrent.Executor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootApplication
@EnableAsync
@EnableMongoAuditing
public class ClassRecoveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClassRecoveryApplication.class, args);
    }

    /**
     * Bounded thread pool for @Async tasks (audio extraction, transcription, analysis).
     * Prevents unbounded thread creation under load.
     *  - corePoolSize=4   : always-available threads for concurrent processing jobs
     *  - maxPoolSize=8    : burst capacity for spikes
     *  - queueCapacity=50 : backlog before rejecting (upload rate limiter)
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("classbridge-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
