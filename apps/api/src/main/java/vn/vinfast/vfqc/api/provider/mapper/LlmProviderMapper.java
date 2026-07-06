package vn.vinfast.vfqc.api.provider.mapper;

import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderResponse;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;
import vn.vinfast.vfqc.api.secret.dto.SecretSummaryResponse;

@Mapper(componentModel = "spring")
public interface LlmProviderMapper {
  
  @Mapping(target = "id", source = "provider.id")
  @Mapping(target = "scope", source = "provider.scope")
  @Mapping(target = "ownerUserId", source = "provider.ownerUserId")
  @Mapping(target = "workspaceId", source = "provider.workspaceId")
  @Mapping(target = "name", source = "provider.name")
  @Mapping(target = "providerType", source = "provider.providerType")
  @Mapping(target = "secret", source = "secret")
  @Mapping(target = "baseUrl", source = "provider.baseUrl")
  @Mapping(target = "defaultModel", source = "provider.defaultModel")
  @Mapping(
      target = "availableModels",
      source = "provider.availableModelsJson",
      qualifiedByName = "valueOrEmpty")
  @Mapping(target = "config", source = "provider.configJson", qualifiedByName = "valueOrEmpty")
  @Mapping(target = "status", source = "provider.status")
  @Mapping(target = "createdAt", source = "provider.createdAt")
  @Mapping(target = "updatedAt", source = "provider.updatedAt")
  LlmProviderResponse toResponse(LlmProvider provider, SecretSummaryResponse secret);

  @Named("valueOrEmpty")
  default Map<String, Object> valueOrEmpty(Map<String, Object> value) {
    return value == null ? Map.of() : value;
  }
}
