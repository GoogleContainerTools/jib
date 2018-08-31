package com.google.cloud.tools.jib.maven;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;

public class SkippedGoalVerifier {

  public static void verifyGoalIsSkipped(TestProject testProject, String goal)
      throws VerificationException, IOException {
    String targetImage = "neverbuilt:maven" + System.nanoTime();

    Verifier verifier = new Verifier(testProject.getProjectRoot().toString());
    verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
    verifier.setAutoclean(false);
    verifier.setSystemProperty("jib.skip", "true");

    verifier.executeGoal("jib:" + goal);

    Path logFile = Paths.get(verifier.getBasedir(), verifier.getLogFileName());
    Assert.assertThat(
        new String(Files.readAllBytes(logFile), Charset.forName("UTF-8")),
        CoreMatchers.containsString(
            "[INFO] Skipping containerization because jib-maven-plugin: skip = true\n"
                + "[INFO] ------------------------------------------------------------------------\n"
                + "[INFO] BUILD SUCCESS"));
  }
}
