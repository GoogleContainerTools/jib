package com.google.cloud.tools.crepecake.cache;

import com.google.common.io.Resources;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CacheReader}. */
public class CacheReaderTest {

  private static Cache testCache;

  @Before
  public void setUp()
      throws CacheMetadataCorruptedException, NotDirectoryException, URISyntaxException {
    Path testCacheFolder = Paths.get(Resources.getResource("cache").toURI());
    testCache = Cache.init(testCacheFolder);
  }

  @Test
  public void testGetLayerFile() throws URISyntaxException, CacheMetadataCorruptedException {
    File expectedFile =
        new File(
            Resources.getResource(
                    "cache/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.tar.gz")
                .toURI());

    CacheReader cacheReader = new CacheReader(testCache);

    Assert.assertEquals(
        expectedFile,
        cacheReader.getLayerFile(
            CachedLayerType.CLASSES,
            new HashSet<>(Collections.singletonList(new File("some/source/directory")))));
    Assert.assertNull(cacheReader.getLayerFile(CachedLayerType.RESOURCES, new HashSet<>()));
    Assert.assertNull(cacheReader.getLayerFile(CachedLayerType.DEPENDENCIES, new HashSet<>()));
    Assert.assertNull(cacheReader.getLayerFile(CachedLayerType.BASE, new HashSet<>()));
  }
}
