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

package com.google.cloud.tools.jib.cli;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.common.io.Resources;
import java.io.Closeable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.rules.TemporaryFolder;

public class TestProject extends TemporaryFolder implements Closeable {

  /** Copies test project {@code projectName} to {@code destination} folder. */
  private static void copyProject(String projectName, Path destination)
      throws IOException, URISyntaxException {
    Path projectPathInResources = Paths.get(Resources.getResource(projectName).toURI());
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

  private Path projectRoot;

  /** Initialize with a specific project directory. */
  public TestProject(String testProjectName) {
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
  }

  void build(String... gradleArguments) throws IOException, InterruptedException {
    List<String> cmd = new ArrayList<>();
    cmd.add("./gradlew");
    cmd.addAll(Arrays.asList(gradleArguments));
    new Command(cmd).setWorkingDir(projectRoot).run();
  }

  public Path getProjectRoot() {
    return projectRoot;
  }
}
