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
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

// TODO: Consolidate with TestProject in jib-maven-plugin.
/** Works with the test Gradle projects in the {@code resources/projects} directory. */
public class TestProject implements BeforeEachCallback {

  private static final String PROJECTS_PATH_IN_RESOURCES = "gradle/projects/";

  Path temporaryDir;

  /** Copies test project {@code projectName} to {@code destination} folder. */
  private static void copyProject(String projectName, Path destination)
      throws IOException, URISyntaxException {
    Path projectPathInResources =
        Paths.get(Resources.getResource(PROJECTS_PATH_IN_RESOURCES + projectName).toURI());
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
  private String gradleVersion = JibPlugin.GRADLE_MIN_VERSION.getVersion();
  private GradleRunner gradleRunner;

  private Path projectRoot;

  /** Initialize with a specific project directory. */
  public TestProject(String testProjectName, Path temporaryDir) {
    this.testProjectName = testProjectName;
    this.temporaryDir = temporaryDir;
  }

  public TestProject withGradleVersion(String version) {
    gradleVersion = version;
    return this;
  }

  public BuildResult build(String... gradleArguments) {
    return gradleRunner.withArguments(gradleArguments).build();
  }

  public BuildResult build(List<String> gradleArguments) {
    return gradleRunner.withArguments(gradleArguments).build();
  }

  public Path getProjectRoot() {
    return projectRoot;
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    projectRoot = Files.createTempDirectory("jib"); // temporaryDir;
    copyProject(testProjectName, projectRoot);

    gradleRunner =
        GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(projectRoot.toFile())
            .withPluginClasspath();
  }
}
