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

import com.google.cloud.tools.jib.maven.TestProject;
import com.google.cloud.tools.jib.plugins.common.SkaffoldFilesOutput;
import com.google.common.base.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Tests for {@link FilesMojoV2}. */
public class FilesMojoV2Test {

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");

  @ClassRule public static final TestProject multiTestProject = new TestProject("multi");

  private static void verifyFiles(
      Path projectRoot,
      String pomXml,
      String module,
      List<String> extraCliOptions,
      List<String> buildFiles,
      List<String> inputFiles,
      List<String> ignoreFiles)
      throws VerificationException, IOException {

    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.addCliOption("--file=" + pomXml);
    verifier.addCliOption("-q");
    if (!Strings.isNullOrEmpty(module)) {
      verifier.addCliOption("-pl");
      verifier.addCliOption(module);
      verifier.addCliOption("-am");
    }
    extraCliOptions.forEach(verifier::addCliOption);
    verifier.executeGoal("jib:" + FilesMojoV2.GOAL_NAME);

    verifier.verifyErrorFreeLog();
    Path logFile = Paths.get(verifier.getBasedir()).resolve(verifier.getLogFileName());
    List<String> log = Files.readAllLines(logFile, StandardCharsets.UTF_8);

    int begin = log.indexOf("BEGIN JIB JSON");
    Assert.assertTrue(begin > -1);
    SkaffoldFilesOutput output = new SkaffoldFilesOutput(log.get(begin + 1));
    Assert.assertEquals(buildFiles, output.getBuild());
    Assert.assertEquals(inputFiles, output.getInputs());
    Assert.assertEquals(ignoreFiles, output.getIgnore());
  }

  @Test
  public void testFilesMojo_singleModule() throws VerificationException, IOException {
    Path projectRoot = simpleTestProject.getProjectRoot();

    verifyFiles(
        projectRoot,
        "pom.xml",
        null,
        Collections.emptyList(),
        Collections.singletonList(projectRoot.resolve("pom.xml").toString()),
        Arrays.asList(
            projectRoot.resolve("src/main/java").toString(),
            projectRoot.resolve("src/main/resources").toString(),
            projectRoot.resolve("src/main/jib-custom").toString()),
        Collections.emptyList());
  }

  @Test
  public void testFilesMojo_singleModuleWithMultipleExtraDirectories()
      throws VerificationException, IOException {
    Path projectRoot = simpleTestProject.getProjectRoot();

    verifyFiles(
        projectRoot,
        "pom-extra-dirs.xml",
        null,
        Collections.emptyList(),
        Collections.singletonList(projectRoot.resolve("pom-extra-dirs.xml").toString()),
        Arrays.asList(
            projectRoot.resolve("src/main/java").toString(),
            projectRoot.resolve("src/main/resources").toString(),
            projectRoot.resolve("src/main/jib-custom").toString(),
            projectRoot.resolve("src/main/jib-custom-2").toString()),
        Collections.emptyList());
  }

  @Test
  public void testFilesMojo_multiModuleSimpleService() throws VerificationException, IOException {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path simpleServiceRoot = projectRoot.resolve("simple-service");

    verifyFiles(
        projectRoot,
        "pom.xml",
        "simple-service",
        Collections.emptyList(),
        Arrays.asList(
            projectRoot.resolve("pom.xml").toString(),
            simpleServiceRoot.resolve("pom.xml").toString()),
        Arrays.asList(
            simpleServiceRoot.resolve("src/main/java").toString(),
            simpleServiceRoot.resolve("src/main/resources").toString(),
            simpleServiceRoot.resolve("src/main/jib").toString()),
        Collections.emptyList());
  }

  @Test
  public void testFilesMojo_multiModuleComplexService() throws VerificationException, IOException {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path complexServiceRoot = projectRoot.resolve("complex-service");
    Path libRoot = projectRoot.resolve("lib");

    verifyFiles(
        projectRoot,
        "pom.xml",
        "complex-service",
        Collections.emptyList(),
        Arrays.asList(
            projectRoot.resolve("pom.xml").toString(),
            libRoot.resolve("pom.xml").toString(),
            complexServiceRoot.resolve("pom.xml").toString()),
        Arrays.asList(
            libRoot.resolve("src/main/java").toString(),
            libRoot.resolve("src/main/resources").toString(),
            complexServiceRoot.resolve("src/main/java").toString(),
            complexServiceRoot.resolve("src/main/resources1").toString(),
            complexServiceRoot.resolve("src/main/resources2").toString(),
            complexServiceRoot.resolve("src/main/jib1").toString(),
            complexServiceRoot.resolve("src/main/jib2").toString(),
            Paths.get("/").toAbsolutePath().resolve("some/random/absolute/path/jib3").toString(),
            // this test expects standard .m2 locations
            Paths.get(
                    System.getProperty("user.home"),
                    ".m2/repository/com/google/cloud/tools/tiny-test-lib/0.0.1-SNAPSHOT/tiny-test-lib-0.0.1-SNAPSHOT.jar")
                .toString()),
        Collections.emptyList());
  }

  @Test
  public void testFilesMojo_extraDirectoriesProperty() throws VerificationException, IOException {
    Path projectRoot = simpleTestProject.getProjectRoot();

    verifyFiles(
        projectRoot,
        "pom.xml",
        null,
        Collections.singletonList(
            "-Djib.extraDirectories.paths=/some/extra/dir,/another/extra/dir"),
        Collections.singletonList(projectRoot.resolve("pom.xml").toString()),
        Arrays.asList(
            projectRoot.resolve("src/main/java").toString(),
            projectRoot.resolve("src/main/resources").toString(),
            Paths.get("/").toAbsolutePath().resolve("some/extra/dir").toString(),
            Paths.get("/").toAbsolutePath().resolve("another/extra/dir").toString()),
        Collections.emptyList());
  }

  @Test
  public void testFilesMojo_skaffoldConfigProperties() throws VerificationException, IOException {
    Path projectRoot = simpleTestProject.getProjectRoot();

    verifyFiles(
        projectRoot,
        "pom-skaffold-config.xml",
        null,
        Collections.emptyList(),
        Arrays.asList(
            projectRoot.resolve("pom-skaffold-config.xml").toString(),
            Paths.get("/abs/path/some.xml").toAbsolutePath().toString()),
        Arrays.asList(
            projectRoot.resolve("src/main/java").toString(),
            projectRoot.resolve("src/main/resources").toString(),
            projectRoot.resolve("src/main/jib-custom").toString(),
            projectRoot.resolve("file/in/project").toString()),
        Arrays.asList(
            projectRoot.resolve("file/to/exclude").toString(),
            projectRoot.resolve("file/to/also/exclude").toString()));
  }
}
