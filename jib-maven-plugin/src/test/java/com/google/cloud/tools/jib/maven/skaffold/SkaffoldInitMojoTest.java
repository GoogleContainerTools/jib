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
import com.google.cloud.tools.jib.plugins.common.SkaffoldInitOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Tests for {@link SkaffoldInitMojo}. */
public class SkaffoldInitMojoTest {

  @ClassRule public static final TestPlugin testPlugin = new TestPlugin();

  @ClassRule
  public static final TestProject simpleTestProject = new TestProject(testPlugin, "simple");

  @ClassRule
  public static final TestProject multiTestProject = new TestProject(testPlugin, "multi");

  /**
   * Verifies that the files task succeeded and returns the list of JSON strings printed by the
   * task.
   *
   * @param project the project to run the task on
   * @return the JSON strings printed by the task
   */
  private static List<String> getJsons(TestProject project)
      throws VerificationException, IOException {
    Verifier verifier = new Verifier(project.getProjectRoot().toString());
    verifier.setAutoclean(false);
    verifier.addCliOption("-q");
    verifier.addCliOption("-Dimage=testimage");
    verifier.executeGoal("jib:" + SkaffoldInitMojo.GOAL_NAME);

    verifier.verifyErrorFreeLog();
    Path logFile = Paths.get(verifier.getBasedir()).resolve(verifier.getLogFileName());
    String output = String.join("\n", Files.readAllLines(logFile, StandardCharsets.UTF_8)).trim();
    Assert.assertThat(output, CoreMatchers.startsWith("BEGIN JIB JSON"));

    Pattern pattern = Pattern.compile("BEGIN JIB JSON\r?\n(\\{.*})");
    Matcher matcher = pattern.matcher(output);
    List<String> jsons = new ArrayList<>();
    while (matcher.find()) {
      jsons.add(matcher.group(1));
    }

    return jsons;
  }

  @Test
  public void testFilesMojo_singleModule() throws IOException, VerificationException {
    List<String> outputs = getJsons(simpleTestProject);
    Assert.assertEquals(1, outputs.size());

    SkaffoldInitOutput skaffoldInitOutput = new SkaffoldInitOutput(outputs.get(0));
    Assert.assertEquals("testimage", skaffoldInitOutput.getImage());
    Assert.assertNull(skaffoldInitOutput.getProject());
  }

  @Test
  public void testFilesMojo_multiModule() throws IOException, VerificationException {
    List<String> outputs = getJsons(multiTestProject);
    Assert.assertEquals(4, outputs.size());

    SkaffoldInitOutput skaffoldInitOutput = new SkaffoldInitOutput(outputs.get(0));
    Assert.assertEquals("testimage", skaffoldInitOutput.getImage());
    Assert.assertNull(skaffoldInitOutput.getProject());

    skaffoldInitOutput = new SkaffoldInitOutput(outputs.get(1));
    Assert.assertEquals("testimage", skaffoldInitOutput.getImage());
    Assert.assertEquals("name-service", skaffoldInitOutput.getProject());

    skaffoldInitOutput = new SkaffoldInitOutput(outputs.get(2));
    Assert.assertEquals("testimage", skaffoldInitOutput.getImage());
    Assert.assertEquals("lib", skaffoldInitOutput.getProject());

    skaffoldInitOutput = new SkaffoldInitOutput(outputs.get(3));
    Assert.assertEquals("testimage", skaffoldInitOutput.getImage());
    Assert.assertEquals("service", skaffoldInitOutput.getProject());
  }
}
