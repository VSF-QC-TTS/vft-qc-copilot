# VinFast QC Copilot - Evaluator Implementation

Version: `v0.2`  
Status: Spring Boot 4.0.7-first Implementation Spec  
Applies to: `apps/api`

---

## 1. Purpose

This document explains evaluator configuration, immutable evaluator versions, rule-based evaluation, LLM judge configuration and field-source discovery.

---

## 2. Design Principles

1. Evaluator belongs to one project.
2. Evaluator type is immutable after creation.
3. EvaluatorVersion is immutable after creation.
4. Editing config creates a new EvaluatorVersion.
5. TestRun locks exact EvaluatorVersion IDs.
6. Rule-based evaluator uses safe declarative operators only.
7. Arbitrary JavaScript is forbidden in MVP.
8. Backend validates config; runner maps to Promptfoo later.
9. LLM judge output uses normalized JSON verdict schema.
10. Judge provider/model must be validated before run creation.
11. Field sources come from dataset schema and target response extraction config.
12. Spring AI may be used only inside provider/judge adapter code, not evaluator entities.

---

## 3. Package Layout

```text
evaluator
  controller/EvaluatorController.java
  controller/EvaluatorVersionController.java
  controller/EvaluatorMetadataController.java
  controller/EvaluatorValidationController.java
  controller/EvaluatorFieldSourceController.java
  dto/request/CreateEvaluatorRequest.java
  dto/request/UpdateEvaluatorRequest.java
  dto/request/CreateEvaluatorVersionRequest.java
  dto/request/ValidateEvaluatorRequest.java
  dto/request/EvaluatorRuleRequest.java
  dto/request/RuleBasedConfigRequest.java
  dto/request/LlmJudgeConfigRequest.java
  dto/request/MixedEvaluatorConfigRequest.java
  dto/response/EvaluatorResponse.java
  dto/response/EvaluatorVersionResponse.java
  dto/response/EvaluatorRuleResponse.java
  dto/response/EvaluatorValidationResponse.java
  dto/response/EvaluatorFieldSourceResponse.java
  dto/response/EvaluatorOperatorResponse.java
  entity/Evaluator.java
  entity/EvaluatorVersion.java
  entity/EvaluatorRule.java
  enums/EvaluatorType.java
  enums/EvaluatorOperator.java
  enums/EvaluatorAggregation.java
  enums/EvaluatorPassConditionType.java
  enums/JudgeOutputSchema.java
  repository/EvaluatorRepository.java
  repository/EvaluatorVersionRepository.java
  repository/EvaluatorRuleRepository.java
  mapper/EvaluatorMapper.java
  mapper/EvaluatorVersionMapper.java
  mapper/EvaluatorRuleMapper.java
  service/EvaluatorService.java
  service/EvaluatorVersionService.java
  service/EvaluatorValidationService.java
  service/EvaluatorFieldSourceService.java
  service/EvaluatorOperatorService.java
  service/EvaluatorLookupService.java
  service/EvaluatorReadinessService.java
  validator/EvaluatorConfigValidator.java
  validator/EvaluatorRuleValidator.java
  validator/LlmJudgeConfigValidator.java
  validator/EvaluatorProviderValidator.java
  model/EvaluatorConfigModel.java
  model/RuleBasedConfigModel.java
  model/LlmJudgeConfigModel.java
```

---

## 4. Entity Rules

### Evaluator

Fields:

```text
id
projectId
name
nameLookup
type
createdBy
createdAt
updatedAt
deletedAt
```

Rules:

1. Type is immutable.
2. Name is unique per active project.
3. Latest version is derived by query or stored if ERD defines it.
4. Soft delete only.

### EvaluatorVersion

Fields:

```text
id
evaluatorId
version
configJson
configHash
createdBy
createdAt
```

Rules:

1. Immutable after insert.
2. Version increments monotonically per evaluator.
3. Config JSON must be canonicalized before hash.
4. Config JSON must not contain secrets.

### EvaluatorRule

Fields:

```text
id
evaluatorVersionId
fieldSource
operator
expectedValueJson
weight
required
orderIndex
configJson
createdAt
updatedAt
```

Rules:

1. Rule rows are immutable with their EvaluatorVersion.
2. New config means new version and new rule rows.
3. Expected value may be literal or dataset variable reference.
4. No script fields.

---

## 5. Evaluator Types

```text
RULE_BASED
LLM_JUDGE
MIXED
```

RULE_BASED:

1. Requires rule-based config.
2. Requires at least one rule.
3. Uses safe operators.

LLM_JUDGE:

1. Requires rubric.
2. Requires normalized output schema.
3. Requires judge provider/model unless system default is configured.

MIXED:

1. MVP should require both rule-based and LLM judge config if UI defines it as combined mode.
2. Aggregation policy must be explicit.

---

## 6. Safe Operators

Recommended MVP operators:

```text
EQUALS
NOT_EQUALS
CONTAINS
NOT_CONTAINS
REGEX_MATCH
STARTS_WITH
ENDS_WITH
JSON_PATH_EXISTS
NUMERIC_EQUALS
NUMERIC_GT
NUMERIC_GTE
NUMERIC_LT
NUMERIC_LTE
IS_EMPTY
IS_NOT_EMPTY
```

Rules:

1. Regex must have timeout/length limits.
2. No JavaScript.
3. No SpEL from users.
4. Operators are implemented by runner/backend adapter safely.

---

## 7. Field Sources

Field sources must be discovered from:

1. Dataset schema columns: `dataset.<columnName>`.
2. Target extracted response fields: `extracted.<fieldName>`.
3. Runtime metadata if contract supports it: `meta.latencyMs`, `meta.statusCode`.

Rules:

1. Do not hardcode UI guesses.
2. Validate field sources during evaluator validation and run preflight.
3. Deleted target/evaluator/dataset changes do not affect historical locked runs.

---

## 8. LLM Judge Config

Recommended structure:

```json
{
  "llmJudge": {
    "rubric": "Grade correctness, completeness and hallucination risk.",
    "scoreScale": { "min": 0, "max": 1 },
    "threshold": 0.7,
    "passCondition": "SCORE_AT_LEAST_THRESHOLD",
    "inputTemplate": "Question: {{dataset.question}}\nExpected: {{dataset.expected}}\nActual: {{extracted.answer}}",
    "outputSchema": "VFQC_JUDGE_V1",
    "defaultJudgeProviderId": "uuid",
    "defaultJudgeModel": "gemini-2.5-flash"
  }
}
```

Rules:

1. Rubric can be long but should not be logged fully.
2. Provider/model compatibility must be validated.
3. Output parser must handle invalid judge JSON as JUDGE_ERROR.
4. Promptfoo mapping happens in runner adapter, not evaluator entity.

---

## 9. Validation API

Validation-only endpoint should:

1. Validate config without persisting.
2. Return warnings/errors.
3. Check field sources.
4. Check provider/model readiness.
5. Check missing required rule fields.
6. Not call external LLM unless API contract explicitly says test judge.

---

## 10. Tests

Required tests:

1. Evaluator type cannot change.
2. New version is created on config edit.
3. Existing version/rules are immutable.
4. Custom JavaScript is rejected.
5. Unknown field source is rejected.
6. Missing judge provider/model is caught.
7. LLM judge config is sanitized and not logged.
