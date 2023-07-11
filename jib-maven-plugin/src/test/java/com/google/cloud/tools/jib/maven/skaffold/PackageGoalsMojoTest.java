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

package com.google.cloud.tools.jib.maven.skaffold;

import com.google.cloud.tools.jib.maven.TestProject;
import com.google.common.base.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link PackageGoalsMojo}. */
@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class PackageGoalsMojoTest {

  @TempDir Path tempDir;

  @RegisterExtension public final TestProject multiTestProject = new TestProject("multi", tempDir);

  private void verifyGoals(Path projectRoot, String profilesString, String... expectedGoals)
      throws VerificationException, IOException {
    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.addCliOption("-q");
    verifier.addCliOption("-pl");
    verifier.addCliOption("complex-service");
    verifier.addCliOption("-am");
    if (!Strings.isNullOrEmpty(profilesString)) {
      verifier.addCliOption("-P" + profilesString);
    }
    verifier.executeGoal("jib:" + PackageGoalsMojo.GOAL_NAME);

    verifier.verifyErrorFreeLog();
    Path logFile = Paths.get(verifier.getBasedir()).resolve(verifier.getLogFileName());
    List<String> log = Files.readAllLines(logFile, StandardCharsets.UTF_8);
    if (log.size() != 0 && log.get(0).startsWith("Picked up JAVA_TOOL_OPTIONS:")) {
      log.remove(0);
    }

    Assert.assertEquals(Arrays.asList(expectedGoals), log);
  }

  @Test
  void testPackageGoalsMojo_complexServiceDefault() throws VerificationException, IOException {
    verifyGoals(multiTestProject.getProjectRoot(), null);
  }

  @Test
  void testPackageGoalsMojo_complexServiceLocalProfile() throws VerificationException, IOException {
    verifyGoals(multiTestProject.getProjectRoot(), "localJib", "dockerBuild");
  }

  @Test
  void testPackageGoalsMojo_complexServiceRemoteProfile()
      throws VerificationException, IOException {
    verifyGoals(multiTestProject.getProjectRoot(), "remoteJib", "build");
  }

  @Test
  void testPackageGoalsMojo_complexServiceMultipleProfiles()
      throws VerificationException, IOException {
    verifyGoals(multiTestProject.getProjectRoot(), "localJib,remoteJib", "dockerBuild", "build");
  }
}
