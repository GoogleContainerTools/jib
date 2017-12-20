package com.google.cloud.tools.crepecake.cache;

import com.google.cloud.tools.crepecake.cache.json.CacheMetadataTemplate;
import com.google.cloud.tools.crepecake.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;

/** Manages the cache. */
public class Cache {

  /** The path to the root of the cache. */
  private final Path cacheDirectory;

  /** The metadata that corresponds to the cache at {@link #cacheDirectory}. */
  private final CacheMetadata cacheMetadata;

  /**
   * Initializes a cache with a directory. This also loads the cache metadata if it exists in the
   * directory.
   */
  public static Cache init(File cacheDirectory)
      throws NotDirectoryException, CacheMetadataCorruptedException {
    if (!cacheDirectory.isDirectory()) {
      throw new NotDirectoryException("The cache can only write to a directory");
    }

    CacheMetadata cacheMetadata = loadCacheMetadata(cacheDirectory.toPath());

    return new Cache(cacheDirectory.toPath(), cacheMetadata);
  }

  private static CacheMetadata loadCacheMetadata(Path cacheDirectory)
      throws CacheMetadataCorruptedException {
    File cacheMetadataJsonFile = cacheDirectory.resolve(CacheFiles.METADATA_FILENAME).toFile();

    if (!cacheMetadataJsonFile.exists()) {
      return new CacheMetadata();
    }

    try {
      CacheMetadataTemplate cacheMetadataJson =
          JsonTemplateMapper.readJsonFromFile(cacheMetadataJsonFile, CacheMetadataTemplate.class);
      return CacheMetadataTranslator.fromTemplate(cacheMetadataJson, cacheDirectory);

    } catch (IOException ex) {
      // The cache metadata is probably corrupted.
      throw new CacheMetadataCorruptedException(ex);
    }
  }

  private Cache(Path cacheDirectory, CacheMetadata cacheMetadata) {
    this.cacheDirectory = cacheDirectory;
    this.cacheMetadata = cacheMetadata;
  }

  @VisibleForTesting
  CacheMetadata getMetadata() {
    return cacheMetadata;
  }
}
