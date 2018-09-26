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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.common.io.Resources;
import java.io.Closeable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.rules.TemporaryFolder;

// TODO: Consolidate with TestProject in jib-maven-plugin.
/** Works with the test Gradle projects in the {@code resources/projects} directory. */
class TestProject extends TemporaryFolder implements Closeable {

  private static final String PROJECTS_PATH_IN_RESOURCES = "projects/";

  /** Copies test project {@code projectName} to {@code destination} folder. */
  private static void copyProject(String projectName, Path destination)
      throws IOException, URISyntaxException {
    Path projectPathInResources =
        Paths.get(Resources.getResource(PROJECTS_PATH_IN_RESOURCES + projectName).toURI());
    // TODO: Consolidate with DockerContextMojo#copyFiles.
    new DirectoryWalker(projectPathInResources)
        .filterRoot()
        .walk(
            path -> {
              // Creates the same path in the destDir.
              Path destPath = destination.resolve(projectPathInResources.relativize(path));
              if (Files.isDirectory(path)) {
                Files.createDirectory(destPath);
              } else {
                Files.copy(path, destPath);
              }
            });
  }

  private final String testProjectName;
  private GradleRunner gradleRunner;

  private Path projectRoot;

  /** Initialize with a specific project directory. */
  TestProject(String testProjectName) {
    this.testProjectName = testProjectName;
  }

  @Override
  public void close() {
    after();
  }

  @Override
  protected void before() throws Throwable {
    super.before();

    projectRoot = newFolder().toPath();
    copyProject(testProjectName, projectRoot);

    gradleRunner = GradleRunner.create().withProjectDir(projectRoot.toFile()).withPluginClasspath();
  }

  BuildResult build(String... gradleArguments) {
    return gradleRunner.withArguments(gradleArguments).build();
  }

  Path getProjectRoot() {
    return projectRoot;
  }
}
