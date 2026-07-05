package vn.vinfast.vfqc.api.provider.mapper;

import org.mapstruct.Mapper;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderSummaryResponse;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;

@Mapper(componentModel = "spring")
public interface LlmProviderMapper {

  LlmProviderSummaryResponse toSummary(LlmProvider provider);
}
