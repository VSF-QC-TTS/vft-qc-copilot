package vn.vinfast.vfqc.api.evaluator.dto;

import jakarta.validation.constraints.Size;

public record UpdateEvaluatorRequest(@Size(max = 160) String name) {}
