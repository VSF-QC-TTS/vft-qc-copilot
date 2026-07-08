package vn.vinfast.vfqc.api.exportjob.model;

import org.springframework.core.io.Resource;

public record ExportDownload(Resource resource, String fileName, String contentType, long sizeBytes) {}
