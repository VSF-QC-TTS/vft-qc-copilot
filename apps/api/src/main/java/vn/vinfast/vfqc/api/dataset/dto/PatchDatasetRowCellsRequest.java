package vn.vinfast.vfqc.api.dataset.dto;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record PatchDatasetRowCellsRequest(
    @Schema(example = "{\"question\":\"Xe VF 8 còn bảo hành không?\",\"expected\":\"Trả lời theo chính sách bảo hành VF 8\"}")
        @NotNull(message = "Dataset row cells are required")
        Map<String, Object> cells,
    @Schema(description = "Revision observed by the client before editing.", example = "0")
        @NotNull(message = "Expected row revision is required")
        Integer expectedRevision) {}
