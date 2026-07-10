package vn.vinfast.vfqc.api.common.config;

import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author nghlong3004 (Long Nguyen Hoang)
 * @since 7/10/2026
 */
@Configuration
public class ApplicationConfig {
	@Bean
	public SecureRandom secureRandom(){
		return new SecureRandom();
	}
}
