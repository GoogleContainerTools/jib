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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.IntegrationTestingConfiguration;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.registry.LocalRegistry;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.time.Instant;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link BuildImageMojo}. */
public class BuildImageMojoIntegrationTest {

  @ClassRule
  public static final LocalRegistry localRegistry1 =
      new LocalRegistry(5000, "testuser", "testpassword");

  @ClassRule
  public static final LocalRegistry localRegistry2 =
      new LocalRegistry(6000, "testuser2", "testpassword2");

  @ClassRule public static final TestPlugin testPlugin = new TestPlugin();

  @ClassRule
  public static final TestProject simpleTestProject = new TestProject(testPlugin, "simple");

  @ClassRule
  public static final TestProject emptyTestProject = new TestProject(testPlugin, "empty");

  @ClassRule
  public static final TestProject skippedTestProject = new TestProject(testPlugin, "empty");

  @ClassRule
  public static final TestProject defaultTargetTestProject =
      new TestProject(testPlugin, "default-target");

  @ClassRule
  public static final TestProject servlet25Project = new TestProject(testPlugin, "war_servlet25");

  private static String getGcrImageReference(String label) {
    String nameBase = "gcr.io/" + IntegrationTestingConfiguration.getGCPProject() + '/';
    return nameBase + label + System.nanoTime();
  }

  static String assertImageDigest(Path projectRoot) throws IOException, DigestException {
    Path digestPath = projectRoot.resolve("target/jib-image.digest");
    Assert.assertTrue(Files.exists(digestPath));
    String digest = new String(Files.readAllBytes(digestPath), StandardCharsets.UTF_8);
    return DescriptorDigest.fromDigest(digest).toString();
  }

  static String assertImageId(Path projectRoot) throws IOException, DigestException {
    Path idPath = projectRoot.resolve("target/jib-image.id");
    Assert.assertTrue(Files.exists(idPath));
    String id = new String(Files.readAllBytes(idPath), StandardCharsets.UTF_8);
    return DescriptorDigest.fromDigest(id).toString();
  }

  /**
   * Builds and runs jib:build on a project at {@code projectRoot} pushing to {@code
   * imageReference}.
   */
  private static String buildAndRun(
      Path projectRoot, String imageReference, boolean runTwice, String pomXml)
      throws VerificationException, IOException, InterruptedException, DigestException {
    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setSystemProperty("_TARGET_IMAGE", imageReference);
    verifier.setAutoclean(false);
    verifier.addCliOption("-X");
    verifier.addCliOption("--file=" + pomXml);
    verifier.executeGoals(Arrays.asList("clean", "compile"));

    // Builds twice, and checks if the second build took less time.
    verifier.executeGoal("jib:build");
    float timeOne = getBuildTimeFromVerifierLog(verifier);

    if (runTwice) {
      verifier.resetStreams();
      verifier.executeGoal("jib:build");
      float timeTwo = getBuildTimeFromVerifierLog(verifier);

      String failMessage = "First build time (%s) is not greater than second build time (%s)";
      Assert.assertTrue(String.format(failMessage, timeOne, timeTwo), timeOne > timeTwo);
    }

    verifier.verifyErrorFreeLog();

    String output = pullAndRunBuiltImage(imageReference);

    try {
      // Test pulling/running using image digest
      String digest = assertImageDigest(projectRoot);
      String imageReferenceWithDigest =
          ImageReference.parse(imageReference).withTag(digest).toString();
      Assert.assertEquals(output, pullAndRunBuiltImage(imageReferenceWithDigest));

      // Test running using image id
      String id = assertImageId(projectRoot);
      Assert.assertNotEquals(digest, id);
      Assert.assertEquals(output, new Command("docker", "run", "--rm", id).run());

    } catch (InvalidImageReferenceException ex) {
      throw new AssertionError("error replacing tag with digest");
    }

    return output;
  }

  private static String buildAndRunAdditionalTag(
      Path projectRoot, String imageReference, String additionalTag)
      throws VerificationException, InvalidImageReferenceException, IOException,
          InterruptedException, DigestException {
    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setSystemProperty("_TARGET_IMAGE", imageReference);
    verifier.setSystemProperty("_ADDITIONAL_TAG", additionalTag);
    verifier.setAutoclean(false);
    verifier.addCliOption("-X");
    verifier.executeGoals(Arrays.asList("clean", "compile", "jib:build"));
    verifier.verifyErrorFreeLog();

    String additionalImageReference =
        ImageReference.parse(imageReference).withTag(additionalTag).toString();

    String output = pullAndRunBuiltImage(imageReference);
    String additionalOutput = pullAndRunBuiltImage(additionalImageReference);
    Assert.assertEquals(output, additionalOutput);

    String digest = assertImageDigest(projectRoot);
    String digestImageReference = ImageReference.parse(imageReference).withTag(digest).toString();
    String digestOutput = pullAndRunBuiltImage(digestImageReference);
    Assert.assertEquals(output, digestOutput);

    assertCreationTimeEpoch(imageReference);
    assertCreationTimeEpoch(additionalImageReference);

    return output;
  }

  private static String buildAndRunComplex(
      String imageReference, String username, String password, LocalRegistry targetRegistry)
      throws VerificationException, IOException, InterruptedException, DigestException {
    Instant before = Instant.now();
    Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setSystemProperty("_TARGET_IMAGE", imageReference);
    verifier.setSystemProperty("_TARGET_USERNAME", username);
    verifier.setSystemProperty("_TARGET_PASSWORD", password);
    verifier.setSystemProperty("sendCredentialsOverHttp", "true");
    verifier.setAutoclean(false);
    verifier.addCliOption("-X");
    verifier.addCliOption("--file=pom-complex.xml");
    verifier.executeGoals(Arrays.asList("clean", "compile", "jib:build"));
    verifier.verifyErrorFreeLog();

    assertImageDigest(simpleTestProject.getProjectRoot());

    // Verify output
    targetRegistry.pull(imageReference);
    assertDockerInspectParameters(imageReference);
    Instant buildTime =
        Instant.parse(
            new Command("docker", "inspect", "-f", "{{.Created}}", imageReference).run().trim());
    Assert.assertTrue(buildTime.isAfter(before) || buildTime.equals(before));
    return new Command("docker", "run", "--rm", imageReference).run();
  }

  /**
   * Pulls a built image and attempts to run it. Also verifies the container configuration and
   * history of the built image.
   *
   * @param imageReference the image reference of the built image
   * @return the container output
   * @throws IOException if an I/O exception occurs
   * @throws InterruptedException if the process was interrupted
   */
  private static String pullAndRunBuiltImage(String imageReference)
      throws IOException, InterruptedException {
    new Command("docker", "pull", imageReference).run();
    return new Command("docker", "run", "--rm", imageReference).run();
  }

  private static void assertDockerInspectParameters(String imageReference)
      throws IOException, InterruptedException {
    String dockerInspect = new Command("docker", "inspect", imageReference).run();
    Assert.assertThat(
        dockerInspect,
        CoreMatchers.containsString(
            "            \"ExposedPorts\": {\n"
                + "                \"1000/tcp\": {},\n"
                + "                \"2000/udp\": {},\n"
                + "                \"2001/udp\": {},\n"
                + "                \"2002/udp\": {},\n"
                + "                \"2003/udp\": {}"));
    Assert.assertThat(
        dockerInspect,
        CoreMatchers.containsString(
            "            \"Labels\": {\n"
                + "                \"key1\": \"value1\",\n"
                + "                \"key2\": \"value2\"\n"
                + "            }"));
    String history = new Command("docker", "history", imageReference).run();
    Assert.assertThat(history, CoreMatchers.containsString("jib-maven-plugin"));
  }

  private static float getBuildTimeFromVerifierLog(Verifier verifier) throws IOException {
    Pattern pattern = Pattern.compile("Building and pushing image : (?<time>.*) ms");

    for (String line :
        Files.readAllLines(Paths.get(verifier.getBasedir(), verifier.getLogFileName()))) {
      Matcher matcher = pattern.matcher(line);
      if (matcher.find()) {
        return Float.parseFloat(matcher.group("time"));
      }
    }

    Assert.fail("Could not find build execution time in logs");
    // Should not reach here.
    return -1;
  }

  private static void assertCreationTimeEpoch(String imageReference)
      throws IOException, InterruptedException {
    Assert.assertEquals(
        "1970-01-01T00:00:00Z",
        new Command("docker", "inspect", "-f", "{{.Created}}", imageReference).run().trim());
  }

  private static void assertWorkingDirectory(String expected, String imageReference)
      throws IOException, InterruptedException {
    Assert.assertEquals(
        expected,
        new Command("docker", "inspect", "-f", "{{.Config.WorkingDir}}", imageReference)
            .run()
            .trim());
  }

  @Nullable private String detachedContainerName;

  @Before
  public void setUp() throws IOException, InterruptedException {
    // Pull distroless to local registry so we can test 'from' credentials
    localRegistry1.pullAndPushToLocal("gcr.io/distroless/java:latest", "distroless/java");
  }

  @After
  public void tearDown() throws IOException, InterruptedException {
    if (detachedContainerName != null) {
      new Command("docker", "stop", detachedContainerName).run();
    }
  }

  @Test
  public void testExecute_simple()
      throws VerificationException, IOException, InterruptedException, DigestException {
    String targetImage = getGcrImageReference("simpleimage:maven");

    // Test empty output error
    try {
      Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
      verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
      verifier.setAutoclean(false);
      verifier.executeGoals(Arrays.asList("clean", "jib:build"));
      Assert.fail();

    } catch (VerificationException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Obtaining project build output files failed; make sure you have compiled your "
                  + "project before trying to build the image. (Did you accidentally run \"mvn "
                  + "clean jib:build\" instead of \"mvn clean compile jib:build\"?)"));
    }

    Instant before = Instant.now();

    // The target registry these tests push to would already have all the layers cached from before,
    // causing this test to fail sometimes with the second build being a bit slower than the first
    // build. This file change makes sure that a new layer is always pushed the first time to solve
    // this issue.
    Files.write(
        simpleTestProject
            .getProjectRoot()
            .resolve("src")
            .resolve("main")
            .resolve("resources")
            .resolve("world"),
        before.toString().getBytes(StandardCharsets.UTF_8));

    Assert.assertEquals(
        "Hello, " + before + ". An argument.\nrw-r--r--\nrw-r--r--\nfoo\ncat\n",
        buildAndRun(simpleTestProject.getProjectRoot(), targetImage, true, "pom.xml"));

    assertDockerInspectParameters(targetImage);
    Instant buildTime =
        Instant.parse(
            new Command("docker", "inspect", "-f", "{{.Created}}", targetImage).run().trim());
    Assert.assertTrue(buildTime.isAfter(before) || buildTime.equals(before));
    assertWorkingDirectory("/home", targetImage);
  }

  @Test
  public void testExecute_empty()
      throws InterruptedException, IOException, VerificationException, DigestException {
    String targetImage = getGcrImageReference("emptyimage:maven");
    Assert.assertEquals(
        "", buildAndRun(emptyTestProject.getProjectRoot(), targetImage, false, "pom.xml"));
    assertDockerInspectParameters(targetImage);
    assertCreationTimeEpoch(targetImage);
    assertWorkingDirectory("", targetImage);
  }

  @Test
  public void testExecute_multipleTags()
      throws IOException, InterruptedException, InvalidImageReferenceException,
          VerificationException, DigestException {
    String targetImage = getGcrImageReference("multitag-image:maven");
    Assert.assertEquals(
        "",
        buildAndRunAdditionalTag(
            emptyTestProject.getProjectRoot(), targetImage, "maven-2" + System.nanoTime()));
  }

  @Test
  public void testExecute_defaultTarget() throws IOException {
    // Test error when 'to' is missing
    try {
      Verifier verifier = new Verifier(defaultTargetTestProject.getProjectRoot().toString());
      verifier.setAutoclean(false);
      verifier.executeGoals(Arrays.asList("clean", "jib:build"));
      Assert.fail();

    } catch (VerificationException ex) {
      Assert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Missing target image parameter, perhaps you should add a <to><image> configuration "
                  + "parameter to your pom.xml or set the parameter via the commandline (e.g. 'mvn "
                  + "compile jib:build -Dimage=<your image name>')."));
    }
  }

  @Test
  public void testExecute_complex()
      throws IOException, InterruptedException, VerificationException, DigestException {
    String targetImage = "localhost:6000/compleximage:maven" + System.nanoTime();
    Assert.assertEquals(
        "Hello, world. An argument.\nrwxr-xr-x\nrwxrwxrwx\nfoo\ncat\n-Xms512m\n-Xdebug\nenvvalue1\nenvvalue2\n",
        buildAndRunComplex(targetImage, "testuser2", "testpassword2", localRegistry2));
    assertWorkingDirectory("", targetImage);
  }

  @Test
  public void testExecute_complex_sameFromAndToRegistry()
      throws IOException, InterruptedException, VerificationException, DigestException {
    String targetImage = "localhost:5000/compleximage:maven" + System.nanoTime();
    Assert.assertEquals(
        "Hello, world. An argument.\nrwxr-xr-x\nrwxrwxrwx\nfoo\ncat\n-Xms512m\n-Xdebug\nenvvalue1\nenvvalue2\n",
        buildAndRunComplex(targetImage, "testuser", "testpassword", localRegistry1));
    assertWorkingDirectory("", targetImage);
  }

  @Test
  public void testExecute_skipJibGoal() throws VerificationException, IOException {
    SkippedGoalVerifier.verifyGoalIsSkipped(skippedTestProject, BuildImageMojo.GOAL_NAME);
  }

  @Test
  public void testExecute_jettyServlet25()
      throws VerificationException, IOException, InterruptedException {
    buildAndRunWar("jetty-servlet25:maven", "pom.xml");
    HttpGetVerifier.verifyBody("Hello world", new URL("http://localhost:8080/hello"));
  }

  @Test
  public void testExecute_tomcatServlet25()
      throws VerificationException, IOException, InterruptedException {
    buildAndRunWar("tomcat-servlet25:maven", "pom-tomcat.xml");
    HttpGetVerifier.verifyBody("Hello world", new URL("http://localhost:8080/hello"));
  }

  @Test
  public void testExecute_javaForced()
      throws VerificationException, IOException, InterruptedException, DigestException {
    String targetImage = getGcrImageReference("java-forced-on-war:maven");
    Assert.assertEquals(
        "Hello from main()\n",
        buildAndRun(servlet25Project.getProjectRoot(), targetImage, false, "pom-java-forced.xml"));
  }

  private void buildAndRunWar(String label, String pomXml)
      throws VerificationException, IOException, InterruptedException {
    String targetImage = getGcrImageReference(label);

    Verifier verifier = new Verifier(servlet25Project.getProjectRoot().toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
    verifier.setAutoclean(false);
    verifier.addCliOption("-X");
    verifier.addCliOption("--file=" + pomXml);
    verifier.executeGoals(Arrays.asList("clean", "package", "jib:build"));
    verifier.verifyErrorFreeLog();

    detachedContainerName =
        new Command("docker", "run", "--rm", "--detach", "-p8080:8080", targetImage).run().trim();
  }
}
