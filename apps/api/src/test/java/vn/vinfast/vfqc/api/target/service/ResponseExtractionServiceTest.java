package vn.vinfast.vfqc.api.target.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.vinfast.vfqc.api.common.error.ApiException;
import vn.vinfast.vfqc.api.target.dto.ResponseExtractionConfigDto;
import vn.vinfast.vfqc.api.target.dto.ResponseExtractionFieldDto;
import vn.vinfast.vfqc.api.target.enums.ResponseExtractionSource;
import vn.vinfast.vfqc.api.target.service.impl.ResponseExtractionServiceImpl;

class ResponseExtractionServiceTest {

  private final ResponseExtractionService service =
      new ResponseExtractionServiceImpl(new ObjectMapper());

  @Test
  void extract_JsonPath_ReturnsConfiguredFields() {
    // Arrange
    var config =
        new ResponseExtractionConfigDto(
            List.of(
                new ResponseExtractionFieldDto(
                    "answer", ResponseExtractionSource.JSON_PATH, "$.choices[0].message", true)));

    // Act
    var extracted = service.extract("{\"choices\":[{\"message\":\"hello\"}]}", config);

    // Assert
    assertThat(extracted).containsEntry("answer", "hello");
  }

  @Test
  void extract_MissingRequiredField_ThrowsValidationError() {
    // Arrange
    var config =
        new ResponseExtractionConfigDto(
            List.of(
                new ResponseExtractionFieldDto(
                    "answer", ResponseExtractionSource.JSON_PATH, "$.answer", true)));

    // Act / Assert
    assertThatThrownBy(() -> service.extract("{\"other\":\"value\"}", config))
        .isInstanceOf(ApiException.class);
  }
}
