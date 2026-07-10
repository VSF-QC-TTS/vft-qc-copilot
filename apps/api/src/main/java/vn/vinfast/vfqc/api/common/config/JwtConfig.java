package vn.vinfast.vfqc.api.common.config;

import static vn.vinfast.vfqc.api.common.constant.JwtConstant.ISSUER;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

/**
 * @author nghlong3004 (Long Nguyen Hoang)
 * @since 7/10/2026
 */
@Configuration
public class JwtConfig {
	@Bean
	public JwtEncoder jwtEncoder(SecurityProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey(properties)));
	}

	@Bean
	public JwtDecoder jwtDecoder(SecurityProperties properties) {
		var decoder =
						NimbusJwtDecoder.withSecretKey(jwtKey(properties))
										.macAlgorithm(MacAlgorithm.HS256)
										.build();

		decoder.setJwtValidator(
						JwtValidators.createDefaultWithIssuer(ISSUER));

		return decoder;
	}

	private SecretKeySpec jwtKey(SecurityProperties properties) {
		var secret = properties.jwt().secretKey();

		if (secret == null
						|| secret.isBlank()
						|| secret.startsWith("${")) {
			throw new IllegalStateException(
							"vfqc.security.jwt.secret-key must be configured");
		}

		var keyBytes = secret.getBytes(StandardCharsets.UTF_8);

		if (keyBytes.length < 32) {
			throw new IllegalStateException(
							"vfqc.security.jwt.secret-key must contain at least 32 UTF-8 bytes");
		}

		return new SecretKeySpec(keyBytes, "HmacSHA256");
	}
}
