/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link CacheLocation}. */
public class CacheLocationTest {

  @Test
  public void testAtPath() {
    Assert.assertEquals(Paths.get("/path/to/cache"), CacheLocation.atPath(Paths.get("/path/to/cache")).getCacheDirectory());
  }

  @Test
  public void testMakeTemporary() throws IOException {
    Assert.assertTrue(Files.exists(CacheLocation.makeTemporary().getCacheDirectory()));
  }

  @Test
  public void testAtDefaultUserLevelCacheDirectory() {
    Assert.assertEquals(CacheLocation.DEFAULT_BASE_CACHE_DIRECTORY, CacheLocation.atDefaultUserLevelCacheDirectory().getCacheDirectory());
  }
}
