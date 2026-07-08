package vn.vinfast.vfqc.api.common.masking;

import org.springframework.stereotype.Component;

@Component
public class FormulaInjectionSanitizer {

  public String sanitize(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    var first = value.charAt(0);
    if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
      return "'" + value;
    }
    return value;
  }
}
