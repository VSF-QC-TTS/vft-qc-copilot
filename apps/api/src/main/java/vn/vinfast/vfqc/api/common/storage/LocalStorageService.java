package vn.vinfast.vfqc.api.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.common.error.ErrorCode;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

  private final StorageProperties properties;

  @Override
  public StoredFile store(String relativePath, byte[] content) {
    log.info("Storing file to Local Storage: relativePath={}, size={} bytes", relativePath, content != null ? content.length : 0);
    var target = resolve(relativePath);
    try {
      Files.createDirectories(target.getParent());
      Files.write(target, content);
      return new StoredFile(relativePath, content.length);
    } catch (IOException exception) {
      throw ErrorCode.INTERNAL_ERROR.throwIt("Export file could not be stored");
    }
  }

  @Override
  public StorageFile load(String relativePath) {
    log.info("Loading file from Local Storage: relativePath={}", relativePath);
    var path = resolve(relativePath);
    if (!Files.isRegularFile(path)) {
      throw ErrorCode.RESOURCE_NOT_FOUND.throwIt();
    }
    try {
      return new StorageFile(new FileSystemResource(path), Files.size(path));
    } catch (IOException exception) {
      throw ErrorCode.RESOURCE_NOT_FOUND.throwIt();
    }
  }

  private Path resolve(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) {
      throw ErrorCode.RESOURCE_NOT_FOUND.throwIt();
    }
    var root = properties.storageRoot().toAbsolutePath().normalize();
    var candidate = Path.of(relativePath);
    if (candidate.isAbsolute()) {
      throw ErrorCode.RESOURCE_NOT_FOUND.throwIt();
    }
    var resolved = root.resolve(candidate).normalize();
    if (!resolved.startsWith(root)) {
      throw ErrorCode.RESOURCE_NOT_FOUND.throwIt();
    }
    return resolved;
  }
}
