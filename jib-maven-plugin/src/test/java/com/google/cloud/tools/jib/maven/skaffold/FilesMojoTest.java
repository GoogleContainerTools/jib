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
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Tests for {@link FilesMojo}. */
public class FilesMojoTest {

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");

  @ClassRule public static final TestProject multiTestProject = new TestProject("multi");

  private static void verifyFiles(Path projectRoot, String pomXml, String module, List<Path> files)
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
    verifier.executeGoal("jib:" + FilesMojo.GOAL_NAME);

    verifier.verifyErrorFreeLog();
    Path logFile = Paths.get(verifier.getBasedir()).resolve(verifier.getLogFileName());
    List<String> log = Files.readAllLines(logFile, StandardCharsets.UTF_8);

    List<String> expectedResult =
        files.stream().map(Path::toAbsolutePath).map(Path::toString).collect(Collectors.toList());

    Assert.assertEquals(expectedResult, log);
  }

  @Test
  public void testFilesMojo_singleModule() throws VerificationException, IOException {
    Path projectRoot = simpleTestProject.getProjectRoot();

    verifyFiles(
        projectRoot,
        "pom.xml",
        null,
        ImmutableList.of(
            projectRoot.resolve("pom.xml"),
            projectRoot.resolve("src/main/java"),
            projectRoot.resolve("src/main/resources"),
            projectRoot.resolve("src/main/jib-custom")));
  }

  @Test
  public void testFilesMojo_singleModuleWithMultipleExtraDirectories()
      throws VerificationException, IOException {
    Path projectRoot = simpleTestProject.getProjectRoot();

    verifyFiles(
        projectRoot,
        "pom-extra-dirs.xml",
        null,
        ImmutableList.of(
            projectRoot.resolve("pom-extra-dirs.xml"),
            projectRoot.resolve("src/main/java"),
            projectRoot.resolve("src/main/resources"),
            projectRoot.resolve("src/main/jib-custom"),
            projectRoot.resolve("src/main/jib-custom-2")));
  }

  @Test
  public void testFilesMojo_multiModuleSimpleService() throws VerificationException, IOException {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path simpleServiceRoot = projectRoot.resolve("simple-service");

    verifyFiles(
        projectRoot,
        "pom.xml",
        "simple-service",
        ImmutableList.of(
            projectRoot.resolve("pom.xml"),
            simpleServiceRoot.resolve("pom.xml"),
            simpleServiceRoot.resolve("src/main/java"),
            simpleServiceRoot.resolve("src/main/resources"),
            simpleServiceRoot.resolve("src/main/jib")));
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
        ImmutableList.of(
            projectRoot.resolve("pom.xml"),
            libRoot.resolve("pom.xml"),
            libRoot.resolve("src/main/java"),
            libRoot.resolve("src/main/resources"),
            complexServiceRoot.resolve("pom.xml"),
            complexServiceRoot.resolve("src/main/java"),
            complexServiceRoot.resolve("src/main/resources1"),
            complexServiceRoot.resolve("src/main/resources2"),
            complexServiceRoot.resolve("src/main/jib1"),
            complexServiceRoot.resolve("src/main/jib2"),
            Paths.get("/some/random/absolute/path/jib3"),
            // this test expects standard .m2 locations
            Paths.get(
                System.getProperty("user.home"),
                ".m2/repository/com/google/guava/guava/HEAD-jre-SNAPSHOT/guava-HEAD-jre-SNAPSHOT.jar")));
  }
}
