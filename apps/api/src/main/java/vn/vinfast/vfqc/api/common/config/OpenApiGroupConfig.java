package vn.vinfast.vfqc.api.common.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiGroupConfig {

  @Bean
  GroupedOpenApi frontendOpenApi() {
    return GroupedOpenApi.builder()
        .group("frontend")
        .pathsToMatch("/api/v1/**")
        .build();
  }
}