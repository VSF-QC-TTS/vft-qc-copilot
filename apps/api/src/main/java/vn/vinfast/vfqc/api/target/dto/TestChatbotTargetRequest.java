package vn.vinfast.vfqc.api.target.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record TestChatbotTargetRequest(
    @Schema(description = "Optional sample values keyed by dataset column name.")
        Map<String, String> datasetValues) {}
