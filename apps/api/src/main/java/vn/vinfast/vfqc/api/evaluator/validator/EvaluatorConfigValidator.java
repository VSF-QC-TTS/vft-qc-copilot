package vn.vinfast.vfqc.api.evaluator.validator;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorRuleRequest;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorAggregation;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorOperator;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorPassConditionType;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorType;
import vn.vinfast.vfqc.api.evaluator.enums.JudgeOutputSchema;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;
import vn.vinfast.vfqc.api.provider.service.LlmProviderLookupService;

@Component
@RequiredArgsConstructor
public class EvaluatorConfigValidator {

  private static final int MAX_RULES = 50;
  private static final Pattern SAFE_JSON_PATH =
      Pattern.compile("^\\$([.\\[][-_A-Za-z0-9'\"\\]]+)*$");
  private static final Set<String> FORBIDDEN_KEYS =
      Set.of("customjavascript", "javascript", "jscode", "script", "functionbody", "function", "eval");
  private static final Set<EvaluatorOperator> EXPECTED_VALUE_OPTIONAL =
      Set.of(EvaluatorOperator.IS_EMPTY, EvaluatorOperator.IS_NOT_EMPTY, EvaluatorOperator.JSON_PATH_EXISTS);
  private static final Set<EvaluatorOperator> NUMERIC_OPERATORS =
      Set.of(
          EvaluatorOperator.NUMERIC_EQUALS,
          EvaluatorOperator.NUMERIC_GT,
          EvaluatorOperator.NUMERIC_GTE,
          EvaluatorOperator.NUMERIC_LT,
          EvaluatorOperator.NUMERIC_LTE);

  private final LlmProviderLookupService providerLookupService;

  public void validate(
      CurrentUser user,
      EvaluatorType type,
      Map<String, Object> config,
      List<EvaluatorRuleRequest> rules,
      Collection<String> availableFieldSources) {
    if (type == null) {
      throw ErrorCode.EVALUATOR_RULE_INVALID.throwIt("Evaluator type is required");
    }
    rejectExecutablePayload(config);
    rejectExecutablePayload(rules == null ? List.of() : rules);
    if (type == EvaluatorType.RULE_BASED || type == EvaluatorType.MIXED) {
      validateRuleBasedConfig(config);
      validateRules(rules, availableFieldSources);
    }
    if (type == EvaluatorType.LLM_JUDGE || type == EvaluatorType.MIXED) {
      validateLlmJudge(user, config);
    }
    if (type == EvaluatorType.LLM_JUDGE && rules != null && !rules.isEmpty()) {
      throw ErrorCode.EVALUATOR_RULE_INVALID.throwIt("LLM judge evaluators must not include rules");
    }
  }

  public void validateRules(List<EvaluatorRuleRequest> rules, Collection<String> availableFieldSources) {
    var safeRules = rules == null ? List.<EvaluatorRuleRequest>of() : rules;
    if (safeRules.isEmpty()) {
      throw ErrorCode.EVALUATOR_RULE_INVALID.throwIt("Rule-based evaluator requires at least one rule");
    }
    if (safeRules.size() > MAX_RULES) {
      throw ErrorCode.EVALUATOR_RULE_INVALID.throwIt("Evaluator has too many rules");
    }
    var orderIndexes = new HashSet<Integer>();
    for (var i = 0; i < safeRules.size(); i++) {
      var rule = safeRules.get(i);
      var orderIndex = rule.orderIndex() == null ? i + 1 : rule.orderIndex();
      if (orderIndex < 1 || !orderIndexes.add(orderIndex)) {
        throw ErrorCode.EVALUATOR_RULE_INVALID.throwIt("Rule order indexes must be unique and positive");
      }
      validateFieldSource(rule.fieldSource(), availableFieldSources);
      validateExpectedValue(rule.operator(), rule.expectedValue());
      validateWeight(rule.weight());
    }
  }

  private void validateFieldSource(String fieldSource, Collection<String> availableFieldSources) {
    if (fieldSource == null || fieldSource.isBlank()) {
      throw ErrorCode.EVALUATOR_FIELD_SOURCE_INVALID.throwIt("Rule field source is required");
    }
    if (fieldSource.contains("javascript:") || fieldSource.contains("<script")) {
      throw ErrorCode.EVALUATOR_CUSTOM_JAVASCRIPT_FORBIDDEN.throwIt();
    }
    var safe =
        fieldSource.equals("response.statusCode")
            || fieldSource.equals("latencyMs")
            || fieldSource.startsWith("extracted.")
            || fieldSource.startsWith("dataset:$dataset.")
            || fieldSource.startsWith("response.body:")
            || fieldSource.startsWith("response.headers:");
    if (!safe) {
      throw ErrorCode.EVALUATOR_FIELD_SOURCE_INVALID.throwIt("Unsupported evaluator field source");
    }
    if (fieldSource.startsWith("response.body:") || fieldSource.startsWith("response.headers:")) {
      var jsonPath = fieldSource.substring(fieldSource.indexOf(':') + 1);
      if (!SAFE_JSON_PATH.matcher(jsonPath).matches()) {
        throw ErrorCode.EVALUATOR_FIELD_SOURCE_INVALID.throwIt("Field source JSONPath is invalid");
      }
    }
    if (availableFieldSources != null
        && !availableFieldSources.isEmpty()
        && !availableFieldSources.contains(fieldSource)
        && !fieldSource.startsWith("response.body:")
        && !fieldSource.startsWith("response.headers:")) {
      throw ErrorCode.EVALUATOR_FIELD_SOURCE_INVALID.throwIt("Field source is not available");
    }
  }

  private void validateExpectedValue(EvaluatorOperator operator, Map<String, Object> expectedValue) {
    if (operator == null) {
      throw ErrorCode.EVALUATOR_RULE_INVALID.throwIt("Rule operator is required");
    }
    if (!EXPECTED_VALUE_OPTIONAL.contains(operator)
        && (expectedValue == null || expectedValue.isEmpty())) {
      throw ErrorCode.EVALUATOR_EXPECTED_VALUE_INVALID.throwIt("Expected value is required");
    }
    if (NUMERIC_OPERATORS.contains(operator)
        && expectedValue != null
        && !isNumericExpectedValue(expectedValue)) {
      throw ErrorCode.EVALUATOR_EXPECTED_VALUE_INVALID.throwIt("Numeric operator requires a numeric expected value");
    }
  }

  private boolean isNumericExpectedValue(Map<String, Object> expectedValue) {
    var value = expectedValue.get("value");
    return value instanceof Number;
  }

  private void validateWeight(BigDecimal weight) {
    if (weight != null && weight.compareTo(BigDecimal.ZERO) <= 0) {
      throw ErrorCode.EVALUATOR_RULE_INVALID.throwIt("Rule weight must be positive");
    }
  }

  @SuppressWarnings("unchecked")
  private void validateRuleBasedConfig(Map<String, Object> config) {
    var ruleBased =
        config == null ? null : config.get("ruleBased") instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    if (ruleBased == null) {
      throw ErrorCode.EVALUATOR_RULE_INVALID.throwIt("Rule-based config is required");
    }
    var aggregation =
        enumValue(
            ruleBased.get("aggregation"),
            EvaluatorAggregation.class,
            ErrorCode.EVALUATOR_RULE_INVALID,
            "Rule-based aggregation is invalid");
    var passCondition =
        ruleBased.get("passCondition") instanceof Map<?, ?> map
            ? (Map<String, Object>) map
            : null;
    if (passCondition == null) {
      throw ErrorCode.EVALUATOR_RULE_INVALID.throwIt("Rule-based pass condition is required");
    }
    var passConditionType =
        enumValue(
            passCondition.get("type"),
            EvaluatorPassConditionType.class,
            ErrorCode.EVALUATOR_RULE_INVALID,
            "Rule-based pass condition type is invalid");
    if (aggregation == EvaluatorAggregation.ALL_PASS
        && passConditionType != EvaluatorPassConditionType.ALL_RULES_PASS) {
      throw ErrorCode.EVALUATOR_RULE_INVALID.throwIt("ALL_PASS requires ALL_RULES_PASS");
    }
    if (aggregation == EvaluatorAggregation.ANY_PASS
        && passConditionType != EvaluatorPassConditionType.ANY_RULE_PASS) {
      throw ErrorCode.EVALUATOR_RULE_INVALID.throwIt("ANY_PASS requires ANY_RULE_PASS");
    }
    if (aggregation == EvaluatorAggregation.ALL_REQUIRED_AND_WEIGHTED_THRESHOLD) {
      if (passConditionType != EvaluatorPassConditionType.SCORE_AT_LEAST_THRESHOLD) {
        throw ErrorCode.EVALUATOR_RULE_INVALID.throwIt(
            "Weighted threshold aggregation requires SCORE_AT_LEAST_THRESHOLD");
      }
      requireProbability(passCondition.get("threshold"), ErrorCode.EVALUATOR_RULE_INVALID, "Rule threshold is invalid");
    }
  }

  @SuppressWarnings("unchecked")
  private void validateLlmJudge(CurrentUser user, Map<String, Object> config) {
    var llmJudge =
        config == null ? null : config.get("llmJudge") instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    if (llmJudge == null) {
      throw ErrorCode.EVALUATOR_LLM_JUDGE_CONFIG_INVALID.throwIt("LLM judge config is required");
    }
    requireText(llmJudge, "rubric");
    requireText(llmJudge, "inputTemplate");
    enumValue(
        llmJudge.get("outputSchema"),
        JudgeOutputSchema.class,
        ErrorCode.EVALUATOR_LLM_JUDGE_CONFIG_INVALID,
        "LLM judge output schema is invalid");
    var passCondition =
        enumValue(
            llmJudge.get("passCondition"),
            EvaluatorPassConditionType.class,
            ErrorCode.EVALUATOR_LLM_JUDGE_CONFIG_INVALID,
            "LLM judge pass condition is invalid");
    if (passCondition != EvaluatorPassConditionType.SCORE_AT_LEAST_THRESHOLD) {
      throw ErrorCode.EVALUATOR_LLM_JUDGE_CONFIG_INVALID.throwIt(
          "LLM judge pass condition must be SCORE_AT_LEAST_THRESHOLD");
    }
    requireProbability(
        llmJudge.get("threshold"),
        ErrorCode.EVALUATOR_LLM_JUDGE_CONFIG_INVALID,
        "LLM judge threshold is invalid");
    var scoreScale =
        llmJudge.get("scoreScale") instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    if (scoreScale == null) {
      throw ErrorCode.EVALUATOR_LLM_JUDGE_CONFIG_INVALID.throwIt("LLM judge score scale is required");
    }
    var min = numeric(scoreScale.get("min"));
    var max = numeric(scoreScale.get("max"));
    if (min == null || max == null || min >= max) {
      throw ErrorCode.EVALUATOR_LLM_JUDGE_CONFIG_INVALID.throwIt("LLM judge score scale is invalid");
    }
    var providerId = uuid(llmJudge.get("defaultJudgeProviderId"));
    if (providerId != null) {
      var provider = providerLookupService.requireVisible(user, providerId);
      if (provider.getStatus() != ProviderStatus.ACTIVE) {
        throw ErrorCode.EVALUATOR_PROVIDER_INVALID.throwIt("Default judge provider must be active");
      }
      var model = llmJudge.get("defaultJudgeModel");
      if ((model == null || model.toString().isBlank())
          && (provider.getDefaultModel() == null || provider.getDefaultModel().isBlank())) {
        throw ErrorCode.EVALUATOR_PROVIDER_INVALID.throwIt("Default judge model is required");
      }
    }
  }

  private <T extends Enum<T>> T enumValue(
      Object value, Class<T> enumType, ErrorCode errorCode, String message) {
    if (value == null || value.toString().isBlank()) {
      throw errorCode.throwIt(message);
    }
    try {
      return Enum.valueOf(enumType, value.toString());
    } catch (IllegalArgumentException exception) {
      throw errorCode.throwIt(message);
    }
  }

  private void requireProbability(Object value, ErrorCode errorCode, String message) {
    var number = numeric(value);
    if (number == null || number < 0 || number > 1) {
      throw errorCode.throwIt(message);
    }
  }

  private Double numeric(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return null;
  }

  private void requireText(Map<String, Object> config, String key) {
    var value = config.get(key);
    if (value == null || value.toString().isBlank()) {
      throw ErrorCode.EVALUATOR_LLM_JUDGE_CONFIG_INVALID.throwIt("LLM judge " + key + " is required");
    }
  }

  private UUID uuid(Object value) {
    if (value == null || value.toString().isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value.toString());
    } catch (IllegalArgumentException exception) {
      throw ErrorCode.EVALUATOR_PROVIDER_INVALID.throwIt("Default judge provider id is invalid");
    }
  }

  @SuppressWarnings("unchecked")
  private void rejectExecutablePayload(Object value) {
    if (value instanceof Map<?, ?> map) {
      for (var entry : map.entrySet()) {
        var key = String.valueOf(entry.getKey()).replaceAll("[^A-Za-z]", "").toLowerCase();
        if (FORBIDDEN_KEYS.contains(key)) {
          throw ErrorCode.EVALUATOR_CUSTOM_JAVASCRIPT_FORBIDDEN.throwIt();
        }
        rejectExecutablePayload(entry.getValue());
      }
    } else if (value instanceof List<?> list) {
      list.forEach(this::rejectExecutablePayload);
    } else if (value instanceof EvaluatorRuleRequest rule) {
      rejectExecutablePayload(rule.fieldSource());
      rejectExecutablePayload(rule.config());
      rejectExecutablePayload(rule.expectedValue());
    } else if (value instanceof String text && text.toLowerCase().contains("<script")) {
      throw ErrorCode.EVALUATOR_CUSTOM_JAVASCRIPT_FORBIDDEN.throwIt();
    }
  }
}
