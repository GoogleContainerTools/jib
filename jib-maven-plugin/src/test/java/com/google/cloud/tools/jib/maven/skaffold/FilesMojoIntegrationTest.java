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

import com.google.cloud.tools.jib.maven.TestPlugin;
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

public class FilesMojoIntegrationTest {

  @ClassRule public static final TestPlugin testPlugin = new TestPlugin();

  @ClassRule
  public static final TestProject simpleTestProject = new TestProject(testPlugin, "simple");

  @ClassRule
  public static final TestProject multiTestProject = new TestProject(testPlugin, "multi");

  private void verifyGoals(Path projectRoot, String module, List<Path> files)
      throws VerificationException, IOException {

    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.setCliOptions(
        Strings.isNullOrEmpty(module)
            ? ImmutableList.of("-q")
            : ImmutableList.of("-q", "-pl", module, "-am"));
    verifier.executeGoal("jib:" + FilesMojo.GOAL_NAME);

    verifier.verifyErrorFreeLog();
    Path logFile = Paths.get(verifier.getBasedir()).resolve(verifier.getLogFileName());
    List<String> log = Files.readAllLines(logFile, StandardCharsets.UTF_8);

    List<String> expectedResult = files.stream().map(Path::toString).collect(Collectors.toList());

    Assert.assertEquals(expectedResult, log);
  }

  @Test
  public void testFilesMojo_singleModule() throws VerificationException, IOException {
    Path projectRoot = simpleTestProject.getProjectRoot();

    verifyGoals(
        projectRoot,
        null,
        ImmutableList.of(projectRoot.resolve("pom.xml"), projectRoot.resolve("src/main/java")));
  }

  @Test
  public void testFilesMojo_multiModuleSimpleService() throws VerificationException, IOException {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path simpleServiceRoot = projectRoot.resolve("simple-service");

    verifyGoals(
        projectRoot,
        "simple-service",
        ImmutableList.of(
            projectRoot.resolve("pom.xml"),
            simpleServiceRoot.resolve("pom.xml"),
            simpleServiceRoot.resolve("src/main/java")));
  }

  @Test
  public void testFilesMojo_multiModuleComplexService() throws VerificationException, IOException {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path complexServiceRoot = projectRoot.resolve("complex-service");
    Path libRoot = projectRoot.resolve("lib");

    verifyGoals(
        projectRoot,
        "complex-service",
        ImmutableList.of(
            projectRoot.resolve("pom.xml"),
            libRoot.resolve("pom.xml"),
            libRoot.resolve("src/main/java"),
            complexServiceRoot.resolve("pom.xml"),
            complexServiceRoot.resolve("src/main/java")));
  }
}
