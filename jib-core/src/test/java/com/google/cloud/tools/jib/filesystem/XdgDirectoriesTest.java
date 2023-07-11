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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link XdgDirectories}. */
class XdgDirectoriesTest {

  @TempDir private Path fakeCacheHome;
  @TempDir private Path fakeConfigHome;

  @Test
  void testGetCacheHome_hasXdgCacheHome() {
    Properties fakeProperties = new Properties();
    fakeProperties.setProperty("user.home", fakeCacheHome.toString());
    Map<String, String> fakeEnvironment =
        ImmutableMap.of("XDG_CACHE_HOME", fakeCacheHome.toString());

    fakeProperties.setProperty("os.name", "linux");
    Assert.assertEquals(
        fakeCacheHome.resolve("google-cloud-tools-java").resolve("jib"),
        XdgDirectories.getCacheHome(fakeProperties, fakeEnvironment));

    fakeProperties.setProperty("os.name", "windows");
    Assert.assertEquals(
        fakeCacheHome.resolve("Google").resolve("Jib").resolve("Cache"),
        XdgDirectories.getCacheHome(fakeProperties, fakeEnvironment));

    fakeProperties.setProperty("os.name", "mac");
    Assert.assertEquals(
        fakeCacheHome.resolve("Google").resolve("Jib"),
        XdgDirectories.getCacheHome(fakeProperties, fakeEnvironment));
  }

  @Test
  void testGetCacheHome_linux() {
    Properties fakeProperties = new Properties();
    fakeProperties.setProperty("user.home", fakeCacheHome.toString());
    fakeProperties.setProperty("os.name", "os is LiNuX");

    Assert.assertEquals(
        Paths.get(fakeCacheHome.toString(), ".cache")
            .resolve("google-cloud-tools-java")
            .resolve("jib"),
        XdgDirectories.getCacheHome(fakeProperties, Collections.emptyMap()));
  }

  @Test
  void testGetCacheHome_windows() {
    Properties fakeProperties = new Properties();
    fakeProperties.setProperty("user.home", "nonexistent");
    fakeProperties.setProperty("os.name", "os is WiNdOwS");

    Map<String, String> fakeEnvironment = ImmutableMap.of("LOCALAPPDATA", fakeCacheHome.toString());

    Assert.assertEquals(
        fakeCacheHome.resolve("Google").resolve("Jib").resolve("Cache"),
        XdgDirectories.getCacheHome(fakeProperties, fakeEnvironment));
  }

  @Test
  void testGetCacheHome_mac() throws IOException {
    Path libraryApplicationSupport = Paths.get(fakeCacheHome.toString(), "Library", "Caches");
    Files.createDirectories(libraryApplicationSupport);

    Properties fakeProperties = new Properties();
    fakeProperties.setProperty("user.home", fakeCacheHome.toString());
    fakeProperties.setProperty("os.name", "os is mAc or DaRwIn");

    Assert.assertEquals(
        libraryApplicationSupport.resolve("Google").resolve("Jib"),
        XdgDirectories.getCacheHome(fakeProperties, Collections.emptyMap()));
  }

  @Test
  void testGetConfigHome_hasXdgConfigHome() {
    Properties fakeProperties = new Properties();
    fakeProperties.setProperty("user.home", fakeConfigHome.toString());
    Map<String, String> fakeEnvironment =
        ImmutableMap.of("XDG_CONFIG_HOME", fakeConfigHome.toString());

    fakeProperties.setProperty("os.name", "linux");
    Assert.assertEquals(
        fakeConfigHome.resolve("google-cloud-tools-java").resolve("jib"),
        XdgDirectories.getConfigHome(fakeProperties, fakeEnvironment));

    fakeProperties.setProperty("os.name", "windows");
    Assert.assertEquals(
        fakeConfigHome.resolve("Google").resolve("Jib").resolve("Config"),
        XdgDirectories.getConfigHome(fakeProperties, fakeEnvironment));

    fakeProperties.setProperty("os.name", "mac");
    Assert.assertEquals(
        fakeConfigHome.resolve("Google").resolve("Jib"),
        XdgDirectories.getConfigHome(fakeProperties, fakeEnvironment));
  }

  @Test
  void testGetConfigHome_linux() {
    Properties fakeProperties = new Properties();
    fakeProperties.setProperty("user.home", fakeConfigHome.toString());
    fakeProperties.setProperty("os.name", "os is LiNuX");

    Assert.assertEquals(
        Paths.get(fakeConfigHome.toString(), ".config")
            .resolve("google-cloud-tools-java")
            .resolve("jib"),
        XdgDirectories.getConfigHome(fakeProperties, Collections.emptyMap()));
  }

  @Test
  void testGetConfigHome_windows() {
    Properties fakeProperties = new Properties();
    fakeProperties.setProperty("user.home", "nonexistent");
    fakeProperties.setProperty("os.name", "os is WiNdOwS");

    Map<String, String> fakeEnvironment =
        ImmutableMap.of("LOCALAPPDATA", fakeConfigHome.toString());

    Assert.assertEquals(
        fakeConfigHome.resolve("Google").resolve("Jib").resolve("Config"),
        XdgDirectories.getConfigHome(fakeProperties, fakeEnvironment));
  }

  @Test
  void testGetConfigHome_mac() throws IOException {
    Path libraryApplicationSupport = Paths.get(fakeConfigHome.toString(), "Library", "Preferences");
    Files.createDirectories(libraryApplicationSupport);

    Properties fakeProperties = new Properties();
    fakeProperties.setProperty("user.home", fakeConfigHome.toString());
    fakeProperties.setProperty("os.name", "os is mAc or DaRwIn");

    Assert.assertEquals(
        libraryApplicationSupport.resolve("Google").resolve("Jib"),
        XdgDirectories.getConfigHome(fakeProperties, Collections.emptyMap()));
  }
}
