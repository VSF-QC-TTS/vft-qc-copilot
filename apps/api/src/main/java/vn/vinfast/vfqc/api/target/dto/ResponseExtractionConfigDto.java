package vn.vinfast.vfqc.api.target.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;

public record ResponseExtractionConfigDto(
    @Schema(description = "Fields extracted from the target response.")
        List<@Valid ResponseExtractionFieldDto> fields) {

  public List<ResponseExtractionFieldDto> safeFields() {
    return fields == null ? List.of() : fields;
  }
}
