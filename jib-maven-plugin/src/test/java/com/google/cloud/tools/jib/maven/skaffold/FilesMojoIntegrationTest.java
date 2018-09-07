package com.google.cloud.tools.jib.maven.skaffold;

import com.google.cloud.tools.jib.maven.TestPlugin;
import com.google.cloud.tools.jib.maven.TestProject;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

  @Test
  public void testFilesMojo_singleModule() throws VerificationException, IOException {
    Path projectRoot = simpleTestProject.getProjectRoot();

    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.setCliOptions(ImmutableList.of("-q"));
    verifier.executeGoal("jib:_skaffold-files");
    // verifier.executeGoal("jib:" + FilesMojo.GOAL_NAME);

    verifier.verifyErrorFreeLog();
    File logFile = new File(verifier.getBasedir(), verifier.getLogFileName());
    String log = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);

    String expectedResult =
        simpleTestProject.getProjectRoot().resolve("pom.xml").toString()
            + "\n"
            + simpleTestProject.getProjectRoot().resolve("src/main/java").toString()
            + "\n";
    Assert.assertEquals(expectedResult, log);
  }

  @Test
  public void testFilesMojo_multiModuleSimpleService() throws VerificationException, IOException {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path simpleServiceRoot = projectRoot.resolve("simple-service");

    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.setCliOptions(ImmutableList.of("-q", "-pl", "simple-service", "-am"));
    verifier.executeGoal("jib:" + FilesMojo.GOAL_NAME);

    verifier.verifyErrorFreeLog();
    File logFile = new File(verifier.getBasedir(), verifier.getLogFileName());
    String log = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);

    String expectedResult =
        ImmutableList.of(
                multiTestProject.getProjectRoot().resolve("pom.xml"),
                simpleServiceRoot.resolve("pom.xml"),
                simpleServiceRoot.resolve("src/main/java"))
            .stream()
            .map(Path::toString)
            .collect(Collectors.joining("\n"));

    Assert.assertEquals(expectedResult + "\n", log);
  }

  @Test
  public void testFilesMojo_multiModuleComplexService() throws VerificationException, IOException {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path complexServiceRoot = projectRoot.resolve("complex-service");
    Path libRoot = projectRoot.resolve("lib");

    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.setCliOptions(ImmutableList.of("-q", "-pl", "complex-service", "-am"));
    verifier.executeGoal("jib:" + FilesMojo.GOAL_NAME);

    verifier.verifyErrorFreeLog();
    File logFile = new File(verifier.getBasedir(), verifier.getLogFileName());
    String log = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);

    String expectedResult =
        ImmutableList.of(
                multiTestProject.getProjectRoot().resolve("pom.xml"),
                libRoot.resolve("pom.xml"),
                libRoot.resolve("src/main/java"),
                complexServiceRoot.resolve("pom.xml"),
                complexServiceRoot.resolve("src/main/java"))
            .stream()
            .map(Path::toString)
            .collect(Collectors.joining("\n"));

    Assert.assertEquals(expectedResult + "\n", log);
  }
}
