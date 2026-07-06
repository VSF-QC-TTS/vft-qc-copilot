package vn.vinfast.vfqc.api.target.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record InspectCurlResponse(
    @Schema(description = "Sanitized cURL safe for preview and persistence")
    String sanitizedCurl,
    List<String> findings,
    TargetRequestConfigDto parsedRequest,
    List<SecretBindingCandidateResponse> secretBindingCandidates) {}
