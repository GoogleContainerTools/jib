package com.google.cloud.tools.jib.maven.skaffold;

import com.google.cloud.tools.jib.maven.TestPlugin;
import com.google.cloud.tools.jib.maven.TestProject;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

public class PackageGoalsMojoIntegrationTest {

  @ClassRule public static final TestPlugin testPlugin = new TestPlugin();

  @ClassRule
  public static final TestProject multiTestProject = new TestProject(testPlugin, "multi");

  @Test
  public void testPackageGoalsMojo_complexServiceDefault()
      throws VerificationException, IOException {
    Path projectRoot = multiTestProject.getProjectRoot();

    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.setCliOptions(ImmutableList.of("-q", "-pl", "complex-service"));
    verifier.executeGoal("jib:" + PackageGoalsMojo.GOAL_NAME);

    verifier.verifyErrorFreeLog();
    File logFile = new File(verifier.getBasedir(), verifier.getLogFileName());
    String log = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);

    Assert.assertEquals("\n", log);
  }

  @Test
  public void testPackageGoalsMojo_complexServiceLocalProfile()
      throws VerificationException, IOException {
    Path projectRoot = multiTestProject.getProjectRoot();

    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.setCliOptions(ImmutableList.of("-q", "-pl", "complex-service", "-PlocalJib"));
    verifier.executeGoal("jib:" + PackageGoalsMojo.GOAL_NAME);

    verifier.verifyErrorFreeLog();
    File logFile = new File(verifier.getBasedir(), verifier.getLogFileName());
    String log = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);

    Assert.assertEquals("dockerBuild\n", log);
  }

  @Test
  public void testPackageGoalsMojo_complexServiceRemoteProfile()
      throws VerificationException, IOException {
    Path projectRoot = multiTestProject.getProjectRoot();

    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.setCliOptions(ImmutableList.of("-q", "-pl", "complex-service", "-PremoteJib"));
    verifier.executeGoal("jib:" + PackageGoalsMojo.GOAL_NAME);

    verifier.verifyErrorFreeLog();
    File logFile = new File(verifier.getBasedir(), verifier.getLogFileName());
    String log = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);

    Assert.assertEquals("build\n", log);
  }

  @Test
  public void testPackageGoalsMojo_complexServiceMultipleProfiles()
      throws VerificationException, IOException {
    Path projectRoot = multiTestProject.getProjectRoot();

    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.setCliOptions(
        ImmutableList.of("-q", "-pl", "complex-service", "-PlocalJib,remoteJib"));
    verifier.executeGoal("jib:" + PackageGoalsMojo.GOAL_NAME);

    verifier.verifyErrorFreeLog();
    File logFile = new File(verifier.getBasedir(), verifier.getLogFileName());
    String log = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);

    Assert.assertEquals("dockerBuild,build\n", log);
  }
}
