package vn.vinfast.vfqc.api.target.service;

import java.util.Map;
import vn.vinfast.vfqc.api.target.dto.ResponseExtractionConfigDto;

public interface ResponseExtractionService {

  /**
   * Extracts configured fields from a sanitized target response body.
   *
   * @param responseBody response body text returned by the target client
   * @param config extraction configuration to apply
   * @return extracted values keyed by configured field name
   * @throws vn.vinfast.vfqc.api.common.error.ApiException when required fields are missing or the
   *     extraction config is invalid
   */
  Map<String, Object> extract(String responseBody, ResponseExtractionConfigDto config);
}
