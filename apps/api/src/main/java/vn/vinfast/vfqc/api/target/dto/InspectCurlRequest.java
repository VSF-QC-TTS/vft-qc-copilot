package vn.vinfast.vfqc.api.target.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InspectCurlRequest(
    @Schema(example = "curl 'https://chatbot.example.test/api' -H 'Authorization: Bearer ...'")
    @NotBlank(message = "Raw cURL is required")
    @Size(max = 20000, message = "Raw cURL must be at most 20000 characters")
    String rawCurl) {}
