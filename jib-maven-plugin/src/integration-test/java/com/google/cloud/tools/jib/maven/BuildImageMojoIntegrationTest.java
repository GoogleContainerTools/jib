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
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.registry.LocalRegistry;
import com.google.common.base.Splitter;
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
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Integration tests for {@link BuildImageMojo}. */
public class BuildImageMojoIntegrationTest {

  @ClassRule
  public static final LocalRegistry localRegistry =
      new LocalRegistry(5000, "testuser", "testpassword");

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");

  @ClassRule public static final TestProject emptyTestProject = new TestProject("empty");

  @ClassRule public static final TestProject skippedTestProject = new TestProject("empty");

  @ClassRule
  public static final TestProject defaultTargetTestProject = new TestProject("default-target");

  @ClassRule public static final TestProject servlet25Project = new TestProject("war_servlet25");

  @ClassRule public static final TestProject springBootProject = new TestProject("spring-boot");

  private static String getTestImageReference(String label) {
    String nameBase = IntegrationTestingConfiguration.getTestRepositoryLocation() + '/';
    return nameBase + label + System.nanoTime();
  }

  static String readDigestFile(Path digestPath) throws IOException, DigestException {
    Assert.assertTrue("File missing: " + digestPath, Files.exists(digestPath));
    String digest = new String(Files.readAllBytes(digestPath), StandardCharsets.UTF_8);
    return DescriptorDigest.fromDigest(digest).toString();
  }

  private static boolean isJava11RuntimeOrHigher() {
    Iterable<String> split = Splitter.on(".").split(System.getProperty("java.version"));
    return Integer.valueOf(split.iterator().next()) >= 11;
  }

  private static Verifier build(
      Path projectRoot, String imageReference, String pomXml, boolean buildTwice)
      throws VerificationException, IOException {
    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setSystemProperty("_TARGET_IMAGE", imageReference);
    if (imageReference.startsWith("localhost")) {
      verifier.setSystemProperty("jib.allowInsecureRegistries", "true");
    }
    verifier.setAutoclean(false);
    verifier.addCliOption("-X");
    verifier.addCliOption("--file=" + pomXml);
    verifier.executeGoals(Arrays.asList("clean", "compile"));

    if (!buildTwice) {
      verifier.executeGoal("jib:build");
      return verifier;
    }

    // Builds twice, and checks if the second build took less time.
    verifier.addCliOption("-Djib.alwaysCacheBaseImage=true");
    verifier.executeGoal("jib:build");
    float timeOne = getBuildTimeFromVerifierLog(verifier);

    verifier.resetStreams();
    verifier.executeGoal("jib:build");
    float timeTwo = getBuildTimeFromVerifierLog(verifier);

    String failMessage = "First build time (%s) is not greater than second build time (%s)";
    Assert.assertTrue(String.format(failMessage, timeOne, timeTwo), timeOne > timeTwo);
    return verifier;
  }

  /**
   * Builds with {@code jib:build} on a project at {@code projectRoot} pushing to {@code
   * imageReference} and run the image after pulling it.
   */
  private static String buildAndRun(
      Path projectRoot, String imageReference, String pomXml, boolean buildTwice)
      throws VerificationException, IOException, InterruptedException, DigestException {
    build(projectRoot, imageReference, pomXml, buildTwice).verifyErrorFreeLog();

    String output = pullAndRunBuiltImage(imageReference);

    try {
      // Test pulling/running using image digest
      String digest = readDigestFile(projectRoot.resolve("target/jib-image.digest"));
      String imageReferenceWithDigest =
          ImageReference.parse(imageReference).withQualifier(digest).toString();
      Assert.assertEquals(output, pullAndRunBuiltImage(imageReferenceWithDigest));

      // Test running using image id
      String id = readDigestFile(projectRoot.resolve("target/jib-image.id"));
      Assert.assertNotEquals(digest, id);
      Assert.assertEquals(output, new Command("docker", "run", "--rm", id).run());

    } catch (InvalidImageReferenceException ex) {
      throw new AssertionError("error replacing tag with digest");
    }

    return output;
  }

  private static String buildAndRunFromLocalBase(
      Path projectRoot, String targetImage, String baseImage, boolean buildTwice)
      throws VerificationException, IOException, InterruptedException {
    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
    verifier.setSystemProperty("_BASE_IMAGE", baseImage);
    verifier.setSystemProperty("jib.allowInsecureRegistries", "true");
    verifier.setAutoclean(false);
    verifier.addCliOption("-X");
    verifier.addCliOption("--file=pom-localbase.xml");
    verifier.executeGoals(Arrays.asList("clean", "compile"));
    if (!buildTwice) {
      verifier.executeGoal("jib:build");
      return pullAndRunBuiltImage(targetImage);
    }

    verifier.executeGoal("jib:build");
    float timeOne = getBuildTimeFromVerifierLog(verifier);

    verifier.resetStreams();
    verifier.executeGoal("jib:build");
    float timeTwo = getBuildTimeFromVerifierLog(verifier);

    String failMessage = "First build time (%s) is not greater than second build time (%s)";
    Assert.assertTrue(String.format(failMessage, timeOne, timeTwo), timeOne > timeTwo);
    return pullAndRunBuiltImage(targetImage);
  }

  private static String buildAndRunAdditionalTag(
      Path projectRoot, String imageReference, String additionalTag)
      throws VerificationException, InvalidImageReferenceException, IOException,
          InterruptedException, DigestException {
    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setSystemProperty("_TARGET_IMAGE", imageReference);
    verifier.setSystemProperty("_ADDITIONAL_TAG", additionalTag);
    if (imageReference.startsWith("localhost")) {
      verifier.setSystemProperty("jib.allowInsecureRegistries", "true");
    }
    verifier.setAutoclean(false);
    verifier.addCliOption("-X");
    verifier.executeGoals(Arrays.asList("clean", "compile", "jib:build"));
    verifier.verifyErrorFreeLog();

    String additionalImageReference =
        ImageReference.parse(imageReference).withQualifier(additionalTag).toString();

    String output = pullAndRunBuiltImage(imageReference);
    String additionalOutput = pullAndRunBuiltImage(additionalImageReference);
    Assert.assertEquals(output, additionalOutput);

    String digest = readDigestFile(projectRoot.resolve("target/jib-image.digest"));
    String digestImageReference =
        ImageReference.parse(imageReference).withQualifier(digest).toString();
    String digestOutput = pullAndRunBuiltImage(digestImageReference);
    Assert.assertEquals(output, digestOutput);

    assertCreationTimeEpoch(imageReference);
    assertCreationTimeEpoch(additionalImageReference);

    return output;
  }

  private static String buildAndRunComplex(String imageReference, String pomFile)
      throws VerificationException, IOException, InterruptedException {
    Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setSystemProperty("_TARGET_IMAGE", imageReference);
    verifier.setSystemProperty("_TARGET_USERNAME", "testuser");
    verifier.setSystemProperty("_TARGET_PASSWORD", "testpassword");
    verifier.setSystemProperty("sendCredentialsOverHttp", "true");
    verifier.setAutoclean(false);
    verifier.addCliOption("-X");
    verifier.addCliOption("--file=" + pomFile);
    verifier.executeGoals(Arrays.asList("clean", "compile", "jib:build"));
    verifier.verifyErrorFreeLog();

    // Verify output
    localRegistry.pull(imageReference);
    assertDockerInspectParameters(imageReference);
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
    assertDockerInspectParameters(imageReference);
    return new Command("docker", "run", "--rm", imageReference).run();
  }

  private static void assertDockerInspectParameters(String imageReference)
      throws IOException, InterruptedException {
    String dockerInspect = new Command("docker", "inspect", imageReference).run();
    MatcherAssert.assertThat(
        dockerInspect,
        CoreMatchers.containsString(
            "            \"ExposedPorts\": {\n"
                + "                \"1000/tcp\": {},\n"
                + "                \"2000/udp\": {},\n"
                + "                \"2001/udp\": {},\n"
                + "                \"2002/udp\": {},\n"
                + "                \"2003/udp\": {}"));
    MatcherAssert.assertThat(
        dockerInspect,
        CoreMatchers.containsString(
            "            \"Labels\": {\n"
                + "                \"key1\": \"value1\",\n"
                + "                \"key2\": \"value2\"\n"
                + "            }"));
    String history = new Command("docker", "history", imageReference).run();
    MatcherAssert.assertThat(history, CoreMatchers.containsString("jib-maven-plugin"));
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

  private static void assertCreationTimeIsAfter(Instant before, String imageReference)
      throws IOException, InterruptedException {
    String inspect =
        new Command("docker", "inspect", "-f", "{{.Created}}", imageReference).run().trim();
    Instant after = Instant.parse(inspect);
    Assert.assertTrue(after.isAfter(before));
  }

  private static void assertWorkingDirectory(String expected, String imageReference)
      throws IOException, InterruptedException {
    Assert.assertEquals(
        expected,
        new Command("docker", "inspect", "-f", "{{.Config.WorkingDir}}", imageReference)
            .run()
            .trim());
  }

  private static void assertEntrypoint(String expected, String imageReference)
      throws IOException, InterruptedException {
    Assert.assertEquals(
        expected,
        new Command("docker", "inspect", "-f", "{{.Config.Entrypoint}}", imageReference)
            .run()
            .trim());
  }

  private static void assertLayerSize(int expected, String imageReference)
      throws IOException, InterruptedException {
    Command command =
        new Command("docker", "inspect", "-f", "{{join .RootFS.Layers \",\"}}", imageReference);
    String layers = command.run().trim();
    Assert.assertEquals(expected, Splitter.on(",").splitToList(layers).size());
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Nullable private String detachedContainerName;

  @Before
  public void setUp() throws IOException, InterruptedException {
    // Pull distroless to local registry so we can test 'from' credentials
    localRegistry.pullAndPushToLocal("gcr.io/distroless/java:latest", "distroless/java");

    // Make sure resource file has a consistent value at the beginning of each test
    // (testExecute_simple overwrites it)
    Files.write(
        simpleTestProject
            .getProjectRoot()
            .resolve("src")
            .resolve("main")
            .resolve("resources")
            .resolve("world"),
        "world".getBytes(StandardCharsets.UTF_8));
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
    String targetImage = getTestImageReference("simpleimage:maven");

    // Test empty output error
    try {
      Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
      verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
      verifier.setAutoclean(false);
      verifier.executeGoals(Arrays.asList("clean", "jib:build"));
      Assert.fail();

    } catch (VerificationException ex) {
      MatcherAssert.assertThat(
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
        "Hello, "
            + before
            + ". An argument.\n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n",
        buildAndRun(simpleTestProject.getProjectRoot(), targetImage, "pom.xml", true));

    assertCreationTimeEpoch(targetImage);
    assertWorkingDirectory("/home", targetImage);
    assertLayerSize(9, targetImage);
  }

  @Test
  public void testBuild_dockerDaemonBase()
      throws IOException, InterruptedException, VerificationException {
    String targetImage =
        IntegrationTestingConfiguration.getTestRepositoryLocation()
            + "/simplewithdockerdaemonbase:maven"
            + System.nanoTime();

    Assert.assertEquals(
        "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n",
        buildAndRunFromLocalBase(
            simpleTestProject.getProjectRoot(),
            targetImage,
            "docker://gcr.io/distroless/java:latest",
            false));
  }

  @Test
  public void testBuild_tarBase() throws IOException, InterruptedException, VerificationException {
    Path path = temporaryFolder.getRoot().toPath().resolve("docker-save-distroless");
    new Command("docker", "save", "gcr.io/distroless/java:latest", "-o", path.toString()).run();
    String targetImage =
        IntegrationTestingConfiguration.getTestRepositoryLocation()
            + "/simplewithtarbase:maven"
            + System.nanoTime();

    Assert.assertEquals(
        "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n",
        buildAndRunFromLocalBase(
            simpleTestProject.getProjectRoot(), targetImage, "tar://" + path, true));
  }

  @Test
  public void testExecute_failOffline() throws IOException {
    String targetImage = getTestImageReference("simpleimageoffline:maven");

    // Test empty output error
    try {
      Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
      verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
      verifier.setAutoclean(false);
      verifier.addCliOption("--offline");
      verifier.executeGoals(Arrays.asList("clean", "compile", "jib:build"));
      Assert.fail();

    } catch (VerificationException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString("Cannot build to a container registry in offline mode"));
    }
  }

  @Test
  public void testExecute_simpleOnJava11()
      throws DigestException, VerificationException, IOException, InterruptedException {
    Assume.assumeTrue(isJava11RuntimeOrHigher());

    String targetImage = getTestImageReference("simpleimage:maven");
    Assert.assertEquals(
        "Hello, world. An argument.\n1970-01-01T00:00:01Z\n",
        buildAndRun(simpleTestProject.getProjectRoot(), targetImage, "pom-java11.xml", false));
  }

  @Test
  public void testExecute_simpleWithIncomptiableJava11()
      throws DigestException, IOException, InterruptedException {
    Assume.assumeTrue(isJava11RuntimeOrHigher());

    try {
      buildAndRun(
          simpleTestProject.getProjectRoot(), "willnotbuild", "pom-java11-incompatible.xml", false);
      Assert.fail();
    } catch (VerificationException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Your project is using Java 11 but the base image is for Java 8, perhaps you should "
                  + "configure a Java 11-compatible base image using the '<from><image>' "
                  + "parameter, or set maven-compiler-plugin's '<target>' or '<release>' version "
                  + "to 8 or below in your build configuration"));
    }
  }

  @Test
  public void testExecute_empty()
      throws InterruptedException, IOException, VerificationException, DigestException {
    String targetImage = getTestImageReference("emptyimage:maven");
    Assert.assertEquals(
        "", buildAndRun(emptyTestProject.getProjectRoot(), targetImage, "pom.xml", false));
    assertCreationTimeEpoch(targetImage);
    assertWorkingDirectory("", targetImage);
  }

  @Test
  public void testExecute_multipleTags()
      throws IOException, InterruptedException, InvalidImageReferenceException,
          VerificationException, DigestException {
    String targetImage = getTestImageReference("multitag-image:maven");
    Assert.assertEquals(
        "",
        buildAndRunAdditionalTag(
            emptyTestProject.getProjectRoot(), targetImage, "maven-2" + System.nanoTime()));
  }

  @Test
  public void testExecute_multipleExtraDirectories()
      throws DigestException, VerificationException, IOException, InterruptedException {
    String targetImage = getTestImageReference("simpleimage:maven");
    Assert.assertEquals(
        "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\nbaz\n1970-01-01T00:00:01Z\n",
        buildAndRun(simpleTestProject.getProjectRoot(), targetImage, "pom-extra-dirs.xml", false));
    assertLayerSize(10, targetImage); // one more than usual
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
      MatcherAssert.assertThat(
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
    String targetImage = "localhost:5000/compleximage:maven" + System.nanoTime();
    Instant before = Instant.now();
    String output = buildAndRunComplex(targetImage, "pom-complex.xml");
    Assert.assertEquals(
        "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrwxr-xr-x\nrwxrwxrwx\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n"
            + "-Xms512m\n-Xdebug\nenvvalue1\nenvvalue2\n",
        output);
    String digest =
        readDigestFile(
            simpleTestProject.getProjectRoot().resolve("target/different-jib-image.digest"));
    String id =
        readDigestFile(simpleTestProject.getProjectRoot().resolve("different-jib-image.id"));
    Assert.assertNotEquals(digest, id);
    Assert.assertEquals(output, new Command("docker", "run", "--rm", id).run());

    assertCreationTimeIsAfter(before, targetImage);
    assertWorkingDirectory("", targetImage);
    assertEntrypoint(
        "[java -Xms512m -Xdebug -cp /other:/app/resources:/app/classes:/app/libs/* "
            + "com.test.HelloWorld]",
        targetImage);
  }

  @Test
  public void testExecute_timestampCustom()
      throws IOException, InterruptedException, VerificationException {
    String targetImage = "localhost:5000/simpleimage:maven" + System.nanoTime();
    String pom = "pom-timestamps-custom.xml";
    Assert.assertEquals(
        "Hello, world. \n2019-06-17T16:30:00Z\nrw-r--r--\nrw-r--r--\n"
            + "foo\ncat\n2019-06-17T16:30:00Z\n2019-06-17T16:30:00Z\n",
        buildAndRunComplex(targetImage, pom));

    String inspect =
        new Command("docker", "inspect", "-f", "{{.Created}}", targetImage).run().trim();
    Instant parsed = Instant.parse(inspect);
    Assert.assertEquals(Instant.parse("2013-11-05T06:29:30Z"), parsed);
  }

  @Test
  public void testExecute_complex_sameFromAndToRegistry()
      throws IOException, InterruptedException, VerificationException {
    String targetImage = "localhost:5000/compleximage:maven" + System.nanoTime();
    Assert.assertEquals(
        "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrwxr-xr-x\nrwxrwxrwx\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n"
            + "-Xms512m\n-Xdebug\nenvvalue1\nenvvalue2\n",
        buildAndRunComplex(targetImage, "pom-complex.xml"));
    assertWorkingDirectory("", targetImage);
  }

  @Test
  public void testExecute_complexProperties()
      throws InterruptedException, VerificationException, IOException {
    String targetImage = "localhost:5000/compleximage:maven" + System.nanoTime();
    Assert.assertEquals(
        "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrwxr-xr-x\nrwxrwxrwx\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n"
            + "-Xms512m\n-Xdebug\nenvvalue1\nenvvalue2\n",
        buildAndRunComplex(targetImage, "pom-complex-properties.xml"));
    assertWorkingDirectory("", targetImage);
  }

  @Test
  public void testExecute_jibSkip() throws VerificationException, IOException {
    SkippedGoalVerifier.verifyJibSkip(skippedTestProject, BuildImageMojo.GOAL_NAME);
  }

  @Test
  public void testExecute_jibContainerizeSkips() throws VerificationException, IOException {
    SkippedGoalVerifier.verifyJibContainerizeSkips(simpleTestProject, BuildDockerMojo.GOAL_NAME);
  }

  @Test
  public void testExecute_jibRequireVersion_ok() throws VerificationException, IOException {
    String targetImage = "localhost:5000/simpleimage:maven" + System.nanoTime();

    Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
    verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
    // properties required to push to :5000 for plain pom.xml
    verifier.setSystemProperty("jib.to.auth.username", "testuser");
    verifier.setSystemProperty("jib.to.auth.password", "testpassword");
    verifier.setSystemProperty("sendCredentialsOverHttp", "true");
    verifier.setSystemProperty("jib.allowInsecureRegistries", "true");
    // this test plugin should match 1.0
    verifier.setSystemProperty("jib.requiredVersion", "1.0");
    verifier.executeGoals(Arrays.asList("package", "jib:build"));
    verifier.verifyErrorFreeLog();
  }

  @Test
  public void testExecute_jibRequireVersion_fail() throws IOException {
    try {
      Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
      // other properties aren't required as this should fail due to jib.requiredVersion
      verifier.setSystemProperty("_TARGET_IMAGE", "ignored");
      // this plugin should be > 1.0 and so jib:build should fail
      verifier.setSystemProperty("jib.requiredVersion", "[,1.0]");
      verifier.executeGoals(Arrays.asList("package", "jib:build"));
      Assert.fail();
    } catch (VerificationException ex) {
      MatcherAssert.assertThat(
          ex.getMessage(), CoreMatchers.containsString("but is required to be [,1.0]"));
    }
  }

  @Test
  public void testExecute_jettyServlet25()
      throws VerificationException, IOException, InterruptedException {
    buildAndRunWebApp(servlet25Project, "jetty-servlet25:maven", "pom.xml");
    HttpGetVerifier.verifyBody("Hello world", new URL("http://localhost:8080/hello"));
  }

  @Test
  public void testExecute_tomcatServlet25()
      throws VerificationException, IOException, InterruptedException {
    buildAndRunWebApp(servlet25Project, "tomcat-servlet25:maven", "pom-tomcat.xml");
    HttpGetVerifier.verifyBody("Hello world", new URL("http://localhost:8080/hello"));
  }

  @Test
  public void testExecute_springBootPackaged()
      throws VerificationException, IOException, InterruptedException {
    buildAndRunWebApp(springBootProject, "spring-boot:maven", "pom.xml");

    String sizeOutput =
        new Command(
                "docker",
                "exec",
                detachedContainerName,
                "/busybox/wc",
                "-c",
                "/app/classpath/spring-boot-0.1.0.original.jar")
            .run();
    MatcherAssert.assertThat(
        sizeOutput, CoreMatchers.containsString(" /app/classpath/spring-boot-0.1.0.original.jar"));
    int fileSize = Integer.parseInt(sizeOutput.substring(0, sizeOutput.indexOf(' ')));
    Assert.assertTrue(fileSize < 3000); // should not be a large fat jar

    HttpGetVerifier.verifyBody("Hello world", new URL("http://localhost:8080"));
  }

  private void buildAndRunWebApp(TestProject project, String label, String pomXml)
      throws VerificationException, IOException, InterruptedException {
    String targetImage = getTestImageReference(label);

    Verifier verifier = new Verifier(project.getProjectRoot().toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
    if (targetImage.startsWith("localhost")) {
      verifier.setSystemProperty("jib.allowInsecureRegistries", "true");
    }
    verifier.setAutoclean(false);
    verifier.addCliOption("-X");
    verifier.addCliOption("--file=" + pomXml);
    verifier.executeGoals(Arrays.asList("clean", "package", "jib:build"));
    verifier.verifyErrorFreeLog();

    detachedContainerName =
        new Command("docker", "run", "--rm", "--detach", "-p8080:8080", targetImage).run().trim();
  }
}
