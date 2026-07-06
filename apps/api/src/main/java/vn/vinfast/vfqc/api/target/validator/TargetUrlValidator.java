package vn.vinfast.vfqc.api.target.validator;

import java.net.URI;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.common.error.ErrorCode;

@Component
public class TargetUrlValidator {

  public void validateForOutboundRequest(String url) {
    var uri = parse(url);
    var scheme = uri.getScheme();
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Target URL must use http or https");
    }
    if (uri.getUserInfo() != null) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Target URL must not contain user info");
    }
    var host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Target URL host is required");
    }
    var normalizedHost = host.toLowerCase();
    if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost")) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Target URL host is not allowed");
    }
    if (isBlockedIpv4(normalizedHost) || isBlockedIpv6(normalizedHost)) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Target URL address range is not allowed");
    }
  }

  private URI parse(String url) {
    try {
      return URI.create(url == null ? "" : url.trim());
    } catch (IllegalArgumentException exception) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Target URL is invalid");
    }
  }

  private boolean isBlockedIpv6(String host) {
    return "::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host);
  }

  private boolean isBlockedIpv4(String host) {
    if (!host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
      return false;
    }
    var parts = host.split("\\.");
    var octets = new int[4];
    for (var i = 0; i < parts.length; i++) {
      octets[i] = Integer.parseInt(parts[i]);
      if (octets[i] < 0 || octets[i] > 255) {
        return true;
      }
    }
    return octets[0] == 10
        || octets[0] == 127
        || octets[0] == 0
        || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
        || (octets[0] == 192 && octets[1] == 168)
        || (octets[0] == 169 && octets[1] == 254);
  }
}
