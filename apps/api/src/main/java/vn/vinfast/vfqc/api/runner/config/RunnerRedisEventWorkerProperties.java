package vn.vinfast.vfqc.api.runner.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "vfqc.runner.redis-events")
public record RunnerRedisEventWorkerProperties(
    @NotNull Boolean enabled, @Min(1) long idleSleepMs, @Min(1) long errorSleepMs) {

  public boolean isEnabled() {
    return Boolean.TRUE.equals(enabled);
  }
}
