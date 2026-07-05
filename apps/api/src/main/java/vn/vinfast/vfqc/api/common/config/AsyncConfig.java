package vn.vinfast.vfqc.api.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's {@code @Async} support for the application.
 *
 * <p>When {@code spring.threads.virtual.enabled=true} is set (dev / prod profiles), Spring Boot
 * auto-configures a {@code SimpleAsyncTaskExecutor} backed by virtual threads. No manual
 * {@code TaskExecutor} bean is needed unless bounded concurrency or a custom exception handler is
 * required.
 *
 * <p>Primary consumer: {@link vn.vinfast.vfqc.api.common.mail.event.MailSendEventListener} which
 * sends emails asynchronously after transaction commit.
 */
@Configuration
@EnableAsync
public class AsyncConfig {}
