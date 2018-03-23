/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.filesystem.PathConsumer;
import com.google.common.io.Resources;
import org.junit.rules.TemporaryFolder;

import java.io.Closeable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// TODO: Consolidate with TestProject in jib-maven-plugin.
/** Works with the test Gradle projects in the {@code resources/projects} directory. */
class TestProject extends TemporaryFolder implements Closeable {

  private static final String PROJECTS_PATH_IN_RESOURCES = "/projects/";

  private final String projectDir;

  /** The temporary root path for the test project. */
  private Path projectRoot;

  /** Initialize with a specific project directory. */
  TestProject(String projectDir) {
    this.projectDir = projectDir;
  }

  @Override
  protected void before() throws Throwable {
    super.before();

    copyProject();
  }

  private void copyProject() throws IOException, URISyntaxException {
    projectRoot = newFolder().toPath();

    Path projectPathInResources = Paths.get(Resources.getResource(PROJECTS_PATH_IN_RESOURCES + projectDir).toURI());
    // TODO: Consolidate with DockerContextMojo#copyFiles.
    new DirectoryWalker(projectPathInResources).filter(path -> !path.equals(projectPathInResources)).walk(path -> {
      // Creates the same path in the destDir.
      Path destPath = projectRoot.resolve(projectPathInResources.relativize(path));
      if (Files.isDirectory(path)) {
        Files.createDirectory(destPath);
      } else {
        Files.copy(path, destPath);
      }
    });

    // TODO: TDODODODODODOODODOO
//    // Puts the correct plugin version into the test project pom.xml.
//    Path pomXml = projectRoot.resolve("pom.xml");
//    Files.write(
//        pomXml,
//        new String(Files.readAllBytes(pomXml), StandardCharsets.UTF_8)
//            .replace("@@PluginVersion@@", testPlugin.getVersion())
//            .getBytes(StandardCharsets.UTF_8));
  }
}
