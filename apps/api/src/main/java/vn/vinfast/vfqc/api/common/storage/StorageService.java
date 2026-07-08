package vn.vinfast.vfqc.api.common.storage;

public interface StorageService {

  /**
   * Stores the given content at the specified relative path.
   *
   * @param relativePath the relative storage path where the content should be stored
   * @param content the binary content to be stored
   * @return stored file metadata including path and size
   */
  StoredFile store(String relativePath, byte[] content);

  /**
   * Loads the file content from the specified relative path.
   *
   * @param relativePath the relative path of the file to load
   * @return storage file metadata and resources
   */
  StorageFile load(String relativePath);
}
