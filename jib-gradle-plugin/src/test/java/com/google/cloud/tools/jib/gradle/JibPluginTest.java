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

package com.google.cloud.tools.jib.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link JibPlugin}. */
public class JibPluginTest {

  @Rule public TemporaryFolder testProjectRoot = new TemporaryFolder();

  @Test
  public void testCheckGradleVersion_pass() {
    GradleRunner.create()
        .withProjectDir(testProjectRoot.getRoot())
        .withPluginClasspath()
        .withGradleVersion(JibPlugin.GRADLE_MIN_VERSION.getVersion())
        .build();
    // pass
  }

  @Test
  public void testCheckGradleVersion_fail() throws IOException {
    try {
      // Copy build file to temp dir
      Path buildFile = testProjectRoot.getRoot().toPath().resolve("build.gradle");
      InputStream buildFileContent =
          getClass().getClassLoader().getResourceAsStream("plugin-test/build.gradle");
      Files.copy(buildFileContent, buildFile);

      GradleRunner.create()
          .withProjectDir(testProjectRoot.getRoot())
          .withPluginClasspath()
          .withGradleVersion("4.3")
          .build();
      Assert.fail();
    } catch (UnexpectedBuildFailure ex) {
      Assert.assertTrue(
          ex.getMessage()
              .contains(
                  "Detected Gradle 4.3, but jib requires "
                      + JibPlugin.GRADLE_MIN_VERSION
                      + " or higher. You can upgrade by running 'gradle wrapper --gradle-version="
                      + JibPlugin.GRADLE_MIN_VERSION.getVersion()
                      + "'."));
    }
  }
}
