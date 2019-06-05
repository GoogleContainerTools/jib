/*
 * Copyright 2019 Google LLC.
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

import com.google.cloud.tools.jib.maven.TestPlugin;
import com.google.cloud.tools.jib.maven.TestProject;
import com.google.cloud.tools.jib.plugins.common.SkaffoldFilesOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Tests for {@link SkaffoldInitMojo}. */
public class SkaffoldInitMojoTest {

  @ClassRule
  public static final TestPlugin testPlugin = new TestPlugin();

  @ClassRule
  public static final TestProject simpleTestProject = new TestProject(testPlugin, "simple");

  @ClassRule
  public static final TestProject multiTestProject = new TestProject(testPlugin, "multi");

  private static void verifyFiles(Path projectRoot)
      throws VerificationException, IOException {

    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.addCliOption("-q");
    verifier.executeGoal("jib:" + FilesMojoV2.GOAL_NAME);

    verifier.verifyErrorFreeLog();
    Path logFile = Paths.get(verifier.getBasedir()).resolve(verifier.getLogFileName());
    List<String> log = Files.readAllLines(logFile, StandardCharsets.UTF_8);

    int begin = log.indexOf("BEGIN JIB JSON");
    Assert.assertTrue(begin > -1);
    SkaffoldFilesOutput output = new SkaffoldFilesOutput(log.get(begin + 1));
    Assert.assertEquals(0, output.getIgnore().size());
  }

  @Test
  public void testFilesMojo_singleModule() throws IOException {
    Path projectRoot = simpleTestProject.getProjectRoot();


  }

  @Test
  public void testFilesMojo_multiModule() throws IOException {
    Path projectRoot = multiTestProject.getProjectRoot();


  }
}
