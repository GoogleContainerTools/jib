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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.apache.maven.it.util.ResourceExtractor;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Works with the test Maven projects in the {@code resources/projects} directory. */
public class TestProject implements BeforeEachCallback {

  private static final String PROJECTS_PATH_IN_RESOURCES = "/maven/projects/";
  private final Path tempDir;

  private static boolean isPomXml(Path path) {
    String filename = path.getFileName().toString();
    return filename.startsWith("pom") && filename.endsWith(".xml");
  }

  private final String projectDir;

  private Path projectRoot;

  /** Initialize to a specific project directory. */
  public TestProject(String projectDir, Path tempDir) {
    this.projectDir = projectDir;
    this.tempDir = tempDir;
  }

  /** Gets the project root resolved as a real path. */
  public Path getProjectRoot() throws IOException {
    return projectRoot.toRealPath();
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    projectRoot = this.tempDir;
    copyProject();
  }

  private void copyProject() throws IOException {
    projectRoot =
        ResourceExtractor.extractResourcePath(
                TestProject.class,
                PROJECTS_PATH_IN_RESOURCES + projectDir,
                Files.createTempDirectory("jib").toFile(),
                true)
            .toPath();

    // Puts the correct plugin version into the test project pom.xml.
    Path gradleProperties = Paths.get("gradle.properties");
    Properties properties = new Properties();
    properties.load(Files.newInputStream(gradleProperties));
    String pluginVersion = properties.getProperty("version");

    new DirectoryWalker(projectRoot)
        .filter(TestProject::isPomXml)
        .walk(
            pomXml ->
                Files.write(
                    pomXml,
                    new String(Files.readAllBytes(pomXml), StandardCharsets.UTF_8)
                        .replace("@@PluginVersion@@", pluginVersion)
                        .getBytes(StandardCharsets.UTF_8)));
  }
}
