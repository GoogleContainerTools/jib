/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.configuration;

import com.google.cloud.tools.jib.cache.CacheDirectoryCreationException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link CacheConfiguration}. */
public class CacheConfigurationTest {

  @Test
  public void testAtPath() {
    CacheConfiguration cacheConfiguration = CacheConfiguration.forPath(Paths.get("/path/to/cache"));
    Assert.assertEquals(Paths.get("/path/to/cache"), cacheConfiguration.getCacheDirectory());
    Assert.assertTrue(cacheConfiguration.shouldEnsureOwnership());
  }

  @Test
  public void testMakeTemporary() throws CacheDirectoryCreationException {
    CacheConfiguration cacheConfiguration = CacheConfiguration.makeTemporary();
    Assert.assertTrue(Files.exists(cacheConfiguration.getCacheDirectory()));
    Assert.assertFalse(cacheConfiguration.shouldEnsureOwnership());
  }

  @Test
  public void testAtDefaultUserLevelCacheDirectory() {
    CacheConfiguration cacheConfiguration = CacheConfiguration.forDefaultUserLevelCacheDirectory();
    Assert.assertEquals(
        CacheConfiguration.DEFAULT_BASE_CACHE_DIRECTORY, cacheConfiguration.getCacheDirectory());
    Assert.assertTrue(cacheConfiguration.shouldEnsureOwnership());
  }
}
