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

package com.google.cloud.tools.jib.filesystem;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/** Tests for {@link UserCacheHome}. */
public class UserCacheHomeTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private String fakeCacheHome;

  @Before
  public void setUp() throws IOException {
    fakeCacheHome = temporaryFolder.newFolder().getPath();
  }

  @Test
  public void testGetCacheHome_hasXdgCacheHome() {
    Map<String, String> fakeEnvironment = ImmutableMap.of("XDG_CACHE_HOME", fakeCacheHome);

    Assert.assertEquals(
        Paths.get(fakeCacheHome),
        UserCacheHome.getCacheHome(Mockito.mock(Properties.class), fakeEnvironment));
  }

  @Test
  public void testGetCacheHome_linux() {
    Properties fakeProperties = new Properties();
    fakeProperties.setProperty("user.home", fakeCacheHome);
    fakeProperties.setProperty("os.name", "os is LiNuX");

    Assert.assertEquals(
        Paths.get(fakeCacheHome, ".cache"),
        UserCacheHome.getCacheHome(fakeProperties, Collections.emptyMap()));
  }

  @Test
  public void testGetCacheHome_windows() {
    Properties fakeProperties = new Properties();
    fakeProperties.setProperty("user.home", "nonexistent");
    fakeProperties.setProperty("os.name", "os is WiNdOwS");

    Map<String, String> fakeEnvironment = ImmutableMap.of("LOCALAPPDATA", fakeCacheHome);

    Assert.assertEquals(
        Paths.get(fakeCacheHome), UserCacheHome.getCacheHome(fakeProperties, fakeEnvironment));
  }

  @Test
  public void testGetCacheHome_mac() throws IOException {
    Path libraryApplicationSupport = Paths.get(fakeCacheHome, "Library", "Application Support");
    Files.createDirectories(libraryApplicationSupport);

    Properties fakeProperties = new Properties();
    fakeProperties.setProperty("user.home", fakeCacheHome);
    fakeProperties.setProperty("os.name", "os is mAc or DaRwIn");

    Assert.assertEquals(
        libraryApplicationSupport,
        UserCacheHome.getCacheHome(fakeProperties, Collections.emptyMap()));
  }
}
