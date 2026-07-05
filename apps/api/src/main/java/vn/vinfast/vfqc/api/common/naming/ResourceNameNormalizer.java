package vn.vinfast.vfqc.api.common.naming;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.common.error.ErrorCode;

@Component
public class ResourceNameNormalizer {

  private static final Pattern REPEATED_WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern FORBIDDEN = Pattern.compile("[\\p{Cntrl}/\\\\]");

  public NormalizedName normalize(String input, int maxLength) {
    if (input == null) {
      throw ErrorCode.INVALID_RESOURCE_NAME.throwIt("Name is required.");
    }
    var displayName = REPEATED_WHITESPACE.matcher(input.trim()).replaceAll(" ");
    if (displayName.isBlank()) {
      throw ErrorCode.INVALID_RESOURCE_NAME.throwIt("Name must not be blank.");
    }
    if (displayName.length() > maxLength) {
      throw ErrorCode.INVALID_RESOURCE_NAME.throwIt("Name is too long.");
    }
    if (FORBIDDEN.matcher(displayName).find()) {
      throw ErrorCode.INVALID_RESOURCE_NAME.throwIt("Name contains unsupported characters.");
    }
    return new NormalizedName(displayName, displayName.toLowerCase(Locale.ROOT));
  }
}
