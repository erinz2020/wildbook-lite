package com.wildme.wildbook_lite.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Custom thread pool for @Async / @EventListener handlers.
 *
 * Interview points:
 *  - 7 core params of ThreadPoolExecutor (corePoolSize, maxPoolSize,
 *    keepAliveTime, workQueue, threadFactory, handler) — pull request
 *    rate, latency, and burstiness all hit these knobs.
 *  - 4 rejection policies: Abort / CallerRuns / Discard / DiscardOldest.
 *    CallerRuns = soft backpressure; AbortPolicy = fail fast.
 *  - Why NOT Executors.newFixedThreadPool / newCachedThreadPool —
 *    unbounded queue → OOM; unbounded threads → context-switch storm.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "applicationTaskExecutor")
    public Executor applicationTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(16);
        exec.setQueueCapacity(200);
        exec.setKeepAliveSeconds(60);
        exec.setThreadNamePrefix("wildbook-async-");
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(20);
        exec.initialize();
        return exec;
    }
}
