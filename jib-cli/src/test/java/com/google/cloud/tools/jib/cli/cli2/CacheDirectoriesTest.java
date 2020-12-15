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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.cloud.tools.jib.filesystem.XdgDirectories;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import picocli.CommandLine;

public class CacheDirectoriesTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testCacheDirectories_defaults() {
    CommonCliOptions commonCliOptions =
        CommandLine.populateCommand(new CommonCliOptions(), "-t", "ignored");
    Path buildContext = Paths.get("some-context");
    CacheDirectories cacheDirectories = CacheDirectories.from(commonCliOptions, buildContext);

    assertThat(cacheDirectories.getBaseImageCache()).isEmpty();
    assertThat(cacheDirectories.getProjectCache())
        .isEqualTo(
            XdgDirectories.getCacheHome()
                .resolve("cli")
                .resolve("projects")
                .resolve(
                    CacheDirectories.getProjectCacheDirectoryFromProject(
                        Paths.get("some-context"))));
  }

  @Test
  public void testCacheDirectories_configuredValuesIgnoresBuildContext() {
    CommonCliOptions commonCliOptions =
        CommandLine.populateCommand(
            new CommonCliOptions(),
            "-t=ignored",
            "--base-image-cache=test-base-image-cache",
            "--project-cache=test-project-cache");
    CacheDirectories cacheDirectories =
        CacheDirectories.from(commonCliOptions, Paths.get("ignored"));

    assertThat(cacheDirectories.getBaseImageCache()).hasValue(Paths.get("test-base-image-cache"));
    assertThat(cacheDirectories.getProjectCache()).isEqualTo(Paths.get("test-project-cache"));
  }

  @Test
  public void testGetProjectCacheDirectoryFromProject_sameFileDifferentPaths() throws IOException {
    temporaryFolder.newFolder("ignored");
    Path path = temporaryFolder.getRoot().toPath();
    Path indirectPath = temporaryFolder.getRoot().toPath().resolve("ignored").resolve("..");

    assertThat(path).isNotEqualTo(indirectPath); // the general equality should not hold true
    assertThat(Files.isSameFile(path, indirectPath)).isTrue(); // path equality holds
    assertThat(CacheDirectories.getProjectCacheDirectoryFromProject(path))
        .isEqualTo(
            CacheDirectories.getProjectCacheDirectoryFromProject(
                indirectPath)); // our hash should hold
  }

  @Test
  public void testGetProjectCacheDirectoryFromProject_different() {
    assertThat(CacheDirectories.getProjectCacheDirectoryFromProject(Paths.get("1")))
        .isNotEqualTo(CacheDirectories.getProjectCacheDirectoryFromProject(Paths.get("2")));
  }
}
