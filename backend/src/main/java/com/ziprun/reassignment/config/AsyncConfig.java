package com.ziprun.reassignment.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

  /**
   * Dedicated executor for the agentic re-planning loop and AI calls so
   * neither runs on the request thread (ADR-4: PATCH /agents/{id}/status
   * must return immediately).
   */
  @Bean(name = "reassignmentExecutor")
  public Executor reassignmentExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("reassign-");
    executor.initialize();
    return executor;
  }
}
