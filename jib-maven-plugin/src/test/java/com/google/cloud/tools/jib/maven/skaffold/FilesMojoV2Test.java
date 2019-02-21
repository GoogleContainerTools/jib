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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
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

/** Tests for {@link FilesMojoV2}. */
public class FilesMojoV2Test {

  @ClassRule public static final TestPlugin testPlugin = new TestPlugin();

  @ClassRule
  public static final TestProject simpleTestProject = new TestProject(testPlugin, "simple");

  @ClassRule
  public static final TestProject multiTestProject = new TestProject(testPlugin, "multi");

  private static void verifyFiles(
      Path projectRoot, String module, List<String> buildFiles, List<String> inputFiles)
      throws VerificationException, IOException {

    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.addCliOption("-q");
    if (!Strings.isNullOrEmpty(module)) {
      verifier.addCliOption("-pl");
      verifier.addCliOption(module);
      verifier.addCliOption("-am");
    }
    verifier.executeGoal("jib:" + FilesMojoV2.GOAL_NAME);

    verifier.verifyErrorFreeLog();
    Path logFile = Paths.get(verifier.getBasedir()).resolve(verifier.getLogFileName());
    List<String> log = Files.readAllLines(logFile, StandardCharsets.UTF_8);

    int begin = log.indexOf("BEGIN JIB JSON");
    Assert.assertTrue(log.size() > begin + 2);
    Assert.assertEquals("END JIB JSON", log.get(begin + 2));

    SkaffoldFilesOutput output = new SkaffoldFilesOutput(log.get(begin + 1));
    Assert.assertEquals(buildFiles, output.getBuild());
    Assert.assertEquals(inputFiles, output.getInputs());
    Assert.assertEquals(0, output.getIgnore().size());
  }

  @Test
  public void testFilesMojo_singleModule() throws VerificationException, IOException {
    Path projectRoot = simpleTestProject.getProjectRoot();

    verifyFiles(
        projectRoot,
        null,
        ImmutableList.of(projectRoot.resolve("pom.xml").toString()),
        ImmutableList.of(
            projectRoot.resolve("src/main/java").toString(),
            projectRoot.resolve("src/main/resources").toString(),
            projectRoot.resolve("src/main/jib-custom").toString()));
  }

  @Test
  public void testFilesMojo_multiModuleSimpleService() throws VerificationException, IOException {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path simpleServiceRoot = projectRoot.resolve("simple-service");

    verifyFiles(
        projectRoot,
        "simple-service",
        ImmutableList.of(
            projectRoot.resolve("pom.xml").toString(),
            simpleServiceRoot.resolve("pom.xml").toString()),
        ImmutableList.of(
            simpleServiceRoot.resolve("src/main/java").toString(),
            simpleServiceRoot.resolve("src/main/resources").toString(),
            simpleServiceRoot.resolve("src/main/jib").toString()));
  }

  @Test
  public void testFilesMojo_multiModuleComplexService() throws VerificationException, IOException {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path complexServiceRoot = projectRoot.resolve("complex-service");
    Path libRoot = projectRoot.resolve("lib");

    verifyFiles(
        projectRoot,
        "complex-service",
        ImmutableList.of(
            projectRoot.resolve("pom.xml").toString(),
            libRoot.resolve("pom.xml").toString(),
            complexServiceRoot.resolve("pom.xml").toString()),
        ImmutableList.of(
            libRoot.resolve("src/main/java").toString(),
            libRoot.resolve("src/main/resources").toString(),
            complexServiceRoot.resolve("src/main/java").toString(),
            complexServiceRoot.resolve("src/main/resources1").toString(),
            complexServiceRoot.resolve("src/main/resources2").toString(),
            complexServiceRoot.resolve("src/main/other-jib").toString(),
            // this test expects standard .m2 locations
            Paths.get(System.getProperty("user.home"))
                .resolve(
                    ".m2/repository/com/google/guava/guava/HEAD-jre-SNAPSHOT/guava-HEAD-jre-SNAPSHOT.jar")
                .toString()));
  }
}
