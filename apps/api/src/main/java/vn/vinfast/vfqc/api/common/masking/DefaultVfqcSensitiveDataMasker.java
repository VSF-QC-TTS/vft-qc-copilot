package vn.vinfast.vfqc.api.common.masking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DefaultVfqcSensitiveDataMasker implements VfqcSensitiveDataMasker {

  private static final String MASK = "****";
  private static final Pattern TOKEN_VALUE =
      Pattern.compile(
          "(Bearer\\s+)[A-Za-z0-9._~+/=-]+|sk-[A-Za-z0-9_-]+|ghp_[A-Za-z0-9_]+|AIza[\\w-]+",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern SENSITIVE_QUERY_PARAM =
      Pattern.compile(
          "([?&](?:api[_-]?key|access[_-]?token|refresh[_-]?token|token|password|secret)=)([^&'\"\\s]+)",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern AUTHORIZATION_BEARER_HEADER =
      Pattern.compile(
          "((?:Authorization)\\s*:\\s*Bearer\\s+)([^'\"\\\\\\n]+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern SENSITIVE_HEADER =
      Pattern.compile(
          "((?:Cookie|X-API-Key|Api-Key|Set-Cookie)\\s*:\\s*)([^'\"\\\\\\n]+)",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern SENSITIVE_JSON_FIELD =
      Pattern.compile(
          "(\"(?:password|token|apiKey|api_key|secret|authorization|cookie)\"\\s*:\\s*\")([^\"]*)(\")",
          Pattern.CASE_INSENSITIVE);

  @Override
  public SanitizationResult<String> sanitizeText(String text) {
    if (text == null) {
      return new SanitizationResult<>(null, List.of());
    }
    var matcher = TOKEN_VALUE.matcher(text);
    var sanitized =
        matcher.replaceAll(
            match -> {
              var value = match.group();
              if (value.toLowerCase().startsWith("bearer ")) {
                return "Bearer " + MASK;
              }
              return MASK;
            });
    return new SanitizationResult<>(
        sanitized, sanitized.equals(text) ? List.of() : List.of("TOKEN_LIKE_VALUE"));
  }

  @Override
  public SanitizationResult<String> sanitizeCurl(String rawCurl) {
    if (rawCurl == null) {
      return new SanitizationResult<>(null, List.of());
    }
    var findings = new ArrayList<String>();
    findings.addAll(sanitizeText(rawCurl).findings());
    var sanitized = replaceSensitiveGroup(rawCurl, AUTHORIZATION_BEARER_HEADER, 1, 2, findings);
    sanitized = replaceSensitiveGroup(sanitized, SENSITIVE_HEADER, 1, 2, findings);
    sanitized = replaceSensitiveGroup(sanitized, SENSITIVE_QUERY_PARAM, 1, 2, findings);
    sanitized = replaceSensitiveGroup(sanitized, SENSITIVE_JSON_FIELD, 1, 2, findings);
    var tokenSanitized = sanitizeText(sanitized);
    findings.addAll(tokenSanitized.findings());
    return new SanitizationResult<>(tokenSanitized.value(), List.copyOf(findings));
  }

  @Override
  public SanitizationResult<Map<String, Object>> sanitizeMap(Map<String, Object> value) {
    if (value == null) {
      return new SanitizationResult<>(Map.of(), List.of());
    }
    var findings = new ArrayList<String>();
    var sanitized = sanitizeMapInternal(value, findings);
    return new SanitizationResult<>(sanitized, List.copyOf(findings));
  }

  private Map<String, Object> sanitizeMapInternal(
      Map<String, Object> source, List<String> findings) {
    var result = new LinkedHashMap<String, Object>();
    for (var entry : source.entrySet()) {
      if (isSensitiveField(entry.getKey())) {
        result.put(entry.getKey(), MASK);
        findings.add("SENSITIVE_FIELD");
        continue;
      }
      result.put(entry.getKey(), sanitizeValue(entry.getValue(), findings));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private Object sanitizeValue(Object value, List<String> findings) {
    if (value instanceof Map<?, ?> map) {
      return sanitizeMapInternal((Map<String, Object>) map, findings);
    }
    if (value instanceof List<?> list) {
      return list.stream().map(item -> sanitizeValue(item, findings)).toList();
    }
    if (value instanceof String stringValue) {
      var sanitized = sanitizeText(stringValue);
      findings.addAll(sanitized.findings());
      return sanitized.value();
    }
    return value;
  }

  private boolean isSensitiveField(String fieldName) {
    var normalized = fieldName.toLowerCase().replace("_", "").replace("-", "").replace(" ", "");
    return normalized.contains("authorization")
        || normalized.contains("apikey")
        || normalized.contains("token")
        || normalized.contains("cookie")
        || normalized.contains("password")
        || normalized.contains("secret")
        || normalized.contains("credential")
        || normalized.contains("privatekey");
  }

  private String replaceSensitiveGroup(
      String input,
      Pattern pattern,
      int prefixGroup,
      int sensitiveGroup,
      List<String> findings) {
    var matcher = pattern.matcher(input);
    var result = new StringBuilder();
    while (matcher.find()) {
      findings.add("SENSITIVE_FIELD");
      var replacement = matcher.group(prefixGroup) + MASK;
      if (matcher.groupCount() > sensitiveGroup) {
        replacement += matcher.group(matcher.groupCount());
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);
    return result.toString();
  }
}
