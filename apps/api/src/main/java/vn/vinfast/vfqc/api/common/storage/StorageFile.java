package vn.vinfast.vfqc.api.common.storage;

import org.springframework.core.io.Resource;

public record StorageFile(Resource resource, long sizeBytes) {}
