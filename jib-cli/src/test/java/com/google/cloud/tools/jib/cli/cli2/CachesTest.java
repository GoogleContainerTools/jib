/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.cli.cli2;

import com.google.cloud.tools.jib.filesystem.XdgDirectories;
import com.google.common.truth.Truth;
import com.google.common.truth.Truth8;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class CachesTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testCaches_defaults() {
    CommonCliOptions commonCliOptions = new CommonCliOptions();
    Path buildContext = Paths.get("some-context");
    Caches caches = Caches.from(commonCliOptions, buildContext);

    Truth8.assertThat(caches.getBaseImageCache()).isEmpty();
    Truth8.assertThat(caches.getProjectCache())
        .isEqualTo(
            XdgDirectories.getCacheHome()
                .resolve("cli")
                .resolve("projects")
                .resolve(Caches.hashPath(Paths.get("some-context"))));
  }

  @Test
  public void testCaches_configuredValuesIgnoresBuildContext() {
    CommonCliOptions commonCliOptions = Mockito.mock(CommonCliOptions.class);
    Path userBaseImageCache = Paths.get("test-base-image-cache");
    Path userProjectCache = Paths.get("test-project-cache");
    Mockito.when(commonCliOptions.getBaseImageCache()).thenReturn(Optional.of(userBaseImageCache));
    Mockito.when(commonCliOptions.getProjectCache()).thenReturn(Optional.of(userProjectCache));
    Caches caches = Caches.from(commonCliOptions, Paths.get("ignored"));

    Truth8.assertThat(caches.getBaseImageCache()).hasValue(userBaseImageCache);
    Truth8.assertThat(caches.getProjectCache()).isEqualTo(userProjectCache);
  }

  @Test
  public void testHashPath_sameFileDifferentPaths() throws IOException {
    temporaryFolder.newFolder("ignored");
    Path path1 = temporaryFolder.getRoot().toPath().resolve("ignored").resolve("..");
    Path path2 = temporaryFolder.getRoot().toPath();

    Assert.assertNotEquals(path1, path2); // the general equality should not hold true
    Assert.assertTrue(Files.isSameFile(path1, path2)); // path equality holds
    Truth.assertThat(Caches.hashPath(path1))
        .isEqualTo(Caches.hashPath(path2)); // our hash should hold
  }

  @Test
  public void testHashPath_different() {
    Truth.assertThat(Caches.hashPath(Paths.get("1"))).isNotEqualTo(Caches.hashPath(Paths.get("2")));
  }
}
