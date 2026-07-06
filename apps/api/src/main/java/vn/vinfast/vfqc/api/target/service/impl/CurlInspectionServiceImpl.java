package vn.vinfast.vfqc.api.target.service.impl;

import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.ProjectPermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.target.dto.InspectCurlRequest;
import vn.vinfast.vfqc.api.target.dto.InspectCurlResponse;
import vn.vinfast.vfqc.api.target.dto.SecretBindingCandidateResponse;
import vn.vinfast.vfqc.api.target.dto.TargetRequestConfigDto;
import vn.vinfast.vfqc.api.target.enums.SecretBindingLocation;
import vn.vinfast.vfqc.api.target.enums.TargetBodyType;
import vn.vinfast.vfqc.api.target.parser.CurlParser;
import vn.vinfast.vfqc.api.target.service.CurlInspectionService;
import vn.vinfast.vfqc.api.target.validator.TargetUrlValidator;

@Service
@RequiredArgsConstructor
public class CurlInspectionServiceImpl implements CurlInspectionService {

  private final ProjectPermissionService projectPermissionService;
  private final VfqcSensitiveDataMasker masker;
  private final CurlParser curlParser;
  private final TargetUrlValidator targetUrlValidator;

  @Override
  @Transactional(readOnly = true)
  public InspectCurlResponse inspect(CurrentUser user, UUID projectId, InspectCurlRequest request) {
    projectPermissionService.requireProject(user, projectId, PermissionAction.EDIT);
    rejectUnsupportedShellSyntax(request.rawCurl());
    var sanitized = masker.sanitizeCurl(request.rawCurl());
    var parsed = curlParser.parse(request.rawCurl());
    targetUrlValidator.validateForOutboundRequest(parsed.url());
    var parsedRequest =
        new TargetRequestConfigDto(
            parsed.method(),
            parsed.url(),
            toStringMap(
                masker.sanitizeMap(new java.util.LinkedHashMap<>(parsed.headers())).value()),
            toStringMap(
                masker.sanitizeMap(new java.util.LinkedHashMap<>(parsed.queryParams())).value()),
            parsed.body().isBlank() ? TargetBodyType.NONE : TargetBodyType.JSON,
            masker.sanitizeText(parsed.body()).value(),
            List.of(),
            List.of());
    return new InspectCurlResponse(
        sanitized.value(), sanitized.findings(), parsedRequest, candidates(parsedRequest));
  }

  private void rejectUnsupportedShellSyntax(String rawCurl) {
    if (rawCurl.contains("|") || rawCurl.contains("$(") || rawCurl.contains("`")) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Unsupported cURL shell syntax");
    }
  }

  private List<SecretBindingCandidateResponse> candidates(TargetRequestConfigDto parsedRequest) {
    var candidates = new ArrayList<SecretBindingCandidateResponse>();
    parsedRequest.safeHeaders().keySet().stream()
        .filter(this::sensitiveKey)
        .map(key -> new SecretBindingCandidateResponse(SecretBindingLocation.HEADER, key))
        .forEach(candidates::add);
    parsedRequest.safeQueryParams().keySet().stream()
        .filter(this::sensitiveKey)
        .map(key -> new SecretBindingCandidateResponse(SecretBindingLocation.QUERY, key))
        .forEach(candidates::add);
    return List.copyOf(candidates);
  }

  private boolean sensitiveKey(String key) {
    var normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    return normalized.contains("authorization")
        || normalized.contains("cookie")
        || normalized.contains("apikey")
        || normalized.contains("token")
        || normalized.contains("secret")
        || normalized.contains("password");
  }

  private Map<String, String> toStringMap(Map<String, Object> value) {
    return value.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue())));
  }
}
