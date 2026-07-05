package vn.vinfast.vfqc.api.dataset.validator;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaColumnRepository;

/**
 * Validates dataset row cells against the schema columns. Checks required column presence and
 * rejects unknown column keys.
 */
@Component
@RequiredArgsConstructor
public class DatasetCellValidator {

  private final DatasetSchemaColumnRepository columnRepository;

  /**
   * Validates that {@code cells} contain all required columns and no unknown columns for the given
   * schema.
   *
   * @param schemaId the dataset schema id
   * @param cells    the cells map to validate
   * @throws vn.vinfast.vfqc.api.common.error.ApiException if validation fails
   */
  public void validate(UUID schemaId, Map<String, Object> cells) {
    var columns = columnRepository.findBySchemaIdOrderByOrderIndexAsc(schemaId);
    var names = new HashSet<String>();
    for (var column : columns) {
      names.add(column.getName());
      if (column.isRequired()
          && (cells.get(column.getName()) == null
              || String.valueOf(cells.get(column.getName())).isBlank())) {
        throw ErrorCode.DATASET_REQUIRED_COLUMN_MISSING.throwIt(
            "Required dataset column is missing: " + column.getName());
      }
    }
    for (var key : cells.keySet()) {
      if (!names.contains(key)) {
        throw ErrorCode.DATASET_UNKNOWN_COLUMN.throwIt("Unknown dataset column: " + key);
      }
    }
  }
}
