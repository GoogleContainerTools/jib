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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.IntegrationTestingConfiguration;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate.ManifestDescriptorTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.LocalRegistry;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Integration tests for {@link BuildImageMojo}. */
public class BuildImageMojoIntegrationTest {

  private static final Logger LOGGER =
      Logger.getLogger(BuildImageMojoIntegrationTest.class.getName());

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

  private static final String dockerHost = localRegistry.getDockerHost();

  private static String getTestImageReference(String label) {
    String nameBase = IntegrationTestingConfiguration.getTestRepositoryLocation() + '/';
    return nameBase + label + System.nanoTime();
  }

  static String readDigestFile(Path digestPath) throws IOException, DigestException {
    assertThat(Files.exists(digestPath)).isTrue();
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
    if (imageReference.startsWith(dockerHost)) {
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

    // The first build should take longer than the second build.
    assertThat(timeOne).isGreaterThan(timeTwo);
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
      assertThat(pullAndRunBuiltImage(imageReferenceWithDigest)).isEqualTo(output);

      // Test running using image id
      String id = readDigestFile(projectRoot.resolve("target/jib-image.id"));
      assertThat(id).isNotEqualTo(digest);
      assertThat(new Command("docker", "run", "--rm", id).run()).isEqualTo(output);

    } catch (InvalidImageReferenceException ex) {
      throw new AssertionError("error replacing tag with digest", ex);
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

    // The first build should take longer than the second build.
    assertThat(timeOne).isGreaterThan(timeTwo);
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
    if (imageReference.startsWith(dockerHost)) {
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
    assertThat(additionalOutput).isEqualTo(output);

    String digest = readDigestFile(projectRoot.resolve("target/jib-image.digest"));
    String digestImageReference =
        ImageReference.parse(imageReference).withQualifier(digest).toString();
    String digestOutput = pullAndRunBuiltImage(digestImageReference);
    assertThat(digestOutput).isEqualTo(output);

    assertThat(getCreationTime(imageReference)).isEqualTo(Instant.EPOCH);
    assertThat(getCreationTime(additionalImageReference)).isEqualTo(Instant.EPOCH);

    return output;
  }

  private static String buildAndRunComplex(String imageReference, String pomFile)
      throws VerificationException, IOException, InterruptedException {
    Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setSystemProperty("_DOCKER_HOST", dockerHost);
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
    String output = new Command("docker", "run", "--rm", imageReference).run();
    if (System.getenv("KOKORO_JOB_CLUSTER") != null
        && System.getenv("KOKORO_JOB_CLUSTER").equals("GCP_UBUNTU_DOCKER")) {
      String containerName = output.trim();
      LOGGER.info("Container name: " + containerName);
      String containerIp = getAndMapContainerIp(containerName);
      LOGGER.info("Mapped container IP to localhost: " + containerIp);
    }
    return output;
  }

  /** Gets container IP and associates it to localhost. */
  private static String getAndMapContainerIp(String containerName) {
    String containerIp;

    // Gets local registry container IP
    List<String> dockerTokens =
        Lists.newArrayList(
            "docker",
            "inspect",
            "-f",
            "'{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}'",
            containerName);
    try {
      String result = new Command(dockerTokens).run();
      // Remove single quotes and LF from result (e.g. '127.0.0.1'\n)
      containerIp = result.replaceAll("['\n]", "");
    } catch (InterruptedException | IOException ex) {
      throw new RuntimeException("Could not get container IP for: " + containerName, ex);
    }

    // Associate container IP with localhost
    try {
      String addHost =
          new Command("bash", "-c", "echo \"" + containerIp + " localhost\" >> /etc/hosts").run();
    } catch (InterruptedException | IOException ex) {
      throw new RuntimeException("Could not associate container IP to localhost: " + containerIp);
    }

    return containerIp;
  }

  private static void assertDockerInspectParameters(String imageReference)
      throws IOException, InterruptedException {
    String dockerInspectExposedPorts =
        new Command("docker", "inspect", "-f", "'{{json .Config.ExposedPorts}}'", imageReference)
            .run();
    String dockerInspectLabels =
        new Command("docker", "inspect", "-f", "'{{json .Config.Labels}}'", imageReference).run();
    String history = new Command("docker", "history", imageReference).run();

    MatcherAssert.assertThat(
        dockerInspectExposedPorts,
        CoreMatchers.containsString(
            "\"1000/tcp\":{},\"2000/udp\":{},\"2001/udp\":{},\"2002/udp\":{},\"2003/udp\":{}"));
    MatcherAssert.assertThat(
        dockerInspectLabels,
        CoreMatchers.containsString("\"key1\":\"value1\",\"key2\":\"value2\""));
    assertThat(history).contains("jib-maven-plugin");
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

    fail("Could not find build execution time in logs");
    // Should not reach here.
    return -1;
  }

  private static Instant getCreationTime(String imageReference)
      throws IOException, InterruptedException {
    String inspect =
        new Command("docker", "inspect", "-f", "{{.Created}}", imageReference).run().trim();
    return Instant.parse(inspect);
  }

  private static String getWorkingDirectory(String imageReference)
      throws IOException, InterruptedException {
    return new Command("docker", "inspect", "-f", "{{.Config.WorkingDir}}", imageReference)
        .run()
        .trim();
  }

  private static String getEntrypoint(String imageReference)
      throws IOException, InterruptedException {
    return new Command("docker", "inspect", "-f", "{{.Config.Entrypoint}}", imageReference)
        .run()
        .trim();
  }

  private static int getLayerSize(String imageReference) throws IOException, InterruptedException {
    Command command =
        new Command("docker", "inspect", "-f", "{{join .RootFS.Layers \",\"}}", imageReference);
    String layers = command.run().trim();
    return Splitter.on(",").splitToList(layers).size();
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Nullable private String detachedContainerName;

  @Before
  public void setUp() throws IOException, InterruptedException {
    // Pull distroless to local registry so we can test 'from' credentials
    localRegistry.pullAndPushToLocal("gcr.io/distroless/java:latest", "distroless/java");

    // Make sure resource file has a consistent value at the beginning of each test
    // (testExecute_simple and testBuild_tarBase overwrite it)
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
      fail();

    } catch (VerificationException ex) {
      assertThat(ex)
          .hasMessageThat()
          .contains(
              "Obtaining project build output files failed; make sure you have compiled your "
                  + "project before trying to build the image. (Did you accidentally run \"mvn "
                  + "clean jib:build\" instead of \"mvn clean compile jib:build\"?)");
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

    assertThat(buildAndRun(simpleTestProject.getProjectRoot(), targetImage, "pom.xml", true))
        .isEqualTo(
            "Hello, "
                + before
                + ". An argument.\n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
                + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n");

    assertThat(getCreationTime(targetImage)).isEqualTo(Instant.EPOCH);
    assertThat(getWorkingDirectory(targetImage)).isEqualTo("/home");
    assertThat(getLayerSize(targetImage)).isEqualTo(10);
  }

  @Test
  public void testBuild_dockerDaemonBase()
      throws IOException, InterruptedException, VerificationException {
    String targetImage =
        IntegrationTestingConfiguration.getTestRepositoryLocation()
            + "/simplewithdockerdaemonbase:maven"
            + System.nanoTime();

    assertThat(
            buildAndRunFromLocalBase(
                simpleTestProject.getProjectRoot(),
                targetImage,
                "docker://gcr.io/distroless/java:latest",
                false))
        .isEqualTo(
            "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
                + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n");
  }

  @Test
  public void testBuild_tarBase() throws IOException, InterruptedException, VerificationException {
    Path path = temporaryFolder.getRoot().toPath().resolve("docker-save-distroless");
    new Command("docker", "save", "gcr.io/distroless/java:latest", "-o", path.toString()).run();
    String targetImage =
        IntegrationTestingConfiguration.getTestRepositoryLocation()
            + "/simplewithtarbase:maven"
            + System.nanoTime();

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

    assertThat(
            buildAndRunFromLocalBase(
                simpleTestProject.getProjectRoot(), targetImage, "tar://" + path, true))
        .isEqualTo(
            "Hello, "
                + before
                + ". An argument.\n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
                + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n");
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
      fail();

    } catch (VerificationException ex) {
      assertThat(ex)
          .hasMessageThat()
          .contains("Cannot build to a container registry in offline mode");
    }
  }

  @Test
  public void testExecute_simpleOnJava11()
      throws DigestException, VerificationException, IOException, InterruptedException {
    Assume.assumeTrue(isJava11RuntimeOrHigher());

    String targetImage = getTestImageReference("simpleimage:maven");
    assertThat(
            buildAndRun(simpleTestProject.getProjectRoot(), targetImage, "pom-java11.xml", false))
        .isEqualTo("Hello, world. An argument.\n1970-01-01T00:00:01Z\n");
  }

  @Test
  public void testExecute_simpleWithIncomptiableJava11()
      throws DigestException, IOException, InterruptedException {
    Assume.assumeTrue(isJava11RuntimeOrHigher());

    try {
      buildAndRun(
          simpleTestProject.getProjectRoot(), "willnotbuild", "pom-java11-incompatible.xml", false);
      fail();
    } catch (VerificationException ex) {
      assertThat(ex)
          .hasMessageThat()
          .contains(
              "Your project is using Java 11 but the base image is for Java 8, perhaps you should "
                  + "configure a Java 11-compatible base image using the '<from><image>' "
                  + "parameter, or set maven-compiler-plugin's '<target>' or '<release>' version "
                  + "to 8 or below in your build configuration");
    }
  }

  @Test
  public void testExecute_empty()
      throws InterruptedException, IOException, VerificationException, DigestException {
    String targetImage = getTestImageReference("emptyimage:maven");
    assertThat(buildAndRun(emptyTestProject.getProjectRoot(), targetImage, "pom.xml", false))
        .isEmpty();
    assertThat(getCreationTime(targetImage)).isEqualTo(Instant.EPOCH);
    assertThat(getWorkingDirectory(targetImage)).isEmpty();
  }

  @Test
  public void testExecute_multipleTags()
      throws IOException, InterruptedException, InvalidImageReferenceException,
          VerificationException, DigestException {
    String targetImage = getTestImageReference("multitag-image:maven");
    assertThat(
            buildAndRunAdditionalTag(
                emptyTestProject.getProjectRoot(), targetImage, "maven-2" + System.nanoTime()))
        .isEmpty();
  }

  @Test
  public void testExecute_multipleExtraDirectories()
      throws DigestException, VerificationException, IOException, InterruptedException {
    String targetImage = getTestImageReference("simpleimage:maven");
    assertThat(
            buildAndRun(
                simpleTestProject.getProjectRoot(), targetImage, "pom-extra-dirs.xml", false))
        .isEqualTo(
            "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
                + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\nbaz\n1970-01-01T00:00:01Z\n");
    assertThat(getLayerSize(targetImage)).isEqualTo(11); // one more than usual
  }

  @Test
  public void testExecute_defaultTarget() throws IOException {
    // Test error when 'to' is missing
    try {
      Verifier verifier = new Verifier(defaultTargetTestProject.getProjectRoot().toString());
      verifier.setAutoclean(false);
      verifier.executeGoals(Arrays.asList("clean", "jib:build"));
      fail();

    } catch (VerificationException ex) {
      assertThat(ex)
          .hasMessageThat()
          .contains(
              "Missing target image parameter, perhaps you should add a <to><image> configuration "
                  + "parameter to your pom.xml or set the parameter via the commandline (e.g. 'mvn "
                  + "compile jib:build -Dimage=<your image name>').");
    }
  }

  @Test
  public void testExecute_complex()
      throws IOException, InterruptedException, VerificationException, DigestException {
    String targetImage = dockerHost + ":5000/compleximage:maven" + System.nanoTime();
    Instant before = Instant.now();
    String output = buildAndRunComplex(targetImage, "pom-complex.xml");
    assertThat(output)
        .isEqualTo(
            "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrwxr-xr-x\nrwxrwxrwx\nfoo\ncat\n"
                + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n"
                + "-Xms512m\n-Xdebug\nenvvalue1\nenvvalue2\n");
    String digest =
        readDigestFile(
            simpleTestProject.getProjectRoot().resolve("target/different-jib-image.digest"));
    String id =
        readDigestFile(simpleTestProject.getProjectRoot().resolve("different-jib-image.id"));
    assertThat(id).isNotEqualTo(digest);
    assertThat(new Command("docker", "run", "--rm", id).run()).isEqualTo(output);

    assertThat(getCreationTime(targetImage)).isGreaterThan(before);
    assertThat(getWorkingDirectory(targetImage)).isEqualTo("/");
    assertThat(getEntrypoint(targetImage))
        .isEqualTo(
            "[java -Xms512m -Xdebug -cp /other:/app/resources:/app/classes:/app/libs/* "
                + "com.test.HelloWorld]");
  }

  @Test
  public void testExecute_timestampCustom()
      throws IOException, InterruptedException, VerificationException {
    String targetImage = dockerHost + ":5000/simpleimage:maven" + System.nanoTime();
    String pom = "pom-timestamps-custom.xml";
    assertThat(buildAndRunComplex(targetImage, pom))
        .isEqualTo(
            "Hello, world. \n2019-06-17T16:30:00Z\nrw-r--r--\nrw-r--r--\n"
                + "foo\ncat\n2019-06-17T16:30:00Z\n2019-06-17T16:30:00Z\n");

    assertThat(getCreationTime(targetImage)).isEqualTo(Instant.parse("2013-11-05T06:29:30Z"));
  }

  @Test
  public void testExecute_complex_sameFromAndToRegistry()
      throws IOException, InterruptedException, VerificationException {
    String targetImage = dockerHost + ":5000/compleximage:maven" + System.nanoTime();
    assertThat(buildAndRunComplex(targetImage, "pom-complex.xml"))
        .isEqualTo(
            "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrwxr-xr-x\nrwxrwxrwx\nfoo\ncat\n"
                + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n"
                + "-Xms512m\n-Xdebug\nenvvalue1\nenvvalue2\n");
    assertThat(getWorkingDirectory(targetImage)).isEqualTo("/");
  }

  @Test
  public void testExecute_complexProperties()
      throws InterruptedException, VerificationException, IOException {
    String targetImage = dockerHost + ":5000/compleximage:maven" + System.nanoTime();
    assertThat(buildAndRunComplex(targetImage, "pom-complex-properties.xml"))
        .isEqualTo(
            "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrwxr-xr-x\nrwxrwxrwx\nfoo\ncat\n"
                + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n"
                + "-Xms512m\n-Xdebug\nenvvalue1\nenvvalue2\n");
    assertThat(getWorkingDirectory(targetImage)).isEqualTo("/");
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
    String targetImage = dockerHost + ":5000/simpleimage:maven" + System.nanoTime();

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
      fail();
    } catch (VerificationException ex) {
      assertThat(ex).hasMessageThat().contains("but is required to be [,1.0]");
    }
  }

  @Test
  public void testExecute_jettyServlet25()
      throws VerificationException, IOException, InterruptedException {
    buildAndRunWebApp(servlet25Project, "jetty-servlet25:maven", "pom.xml");
    HttpGetVerifier.verifyBody("Hello world", new URL("http://" + dockerHost + ":8080/hello"));
  }

  @Test
  public void testExecute_tomcatServlet25()
      throws VerificationException, IOException, InterruptedException {
    buildAndRunWebApp(servlet25Project, "tomcat-servlet25:maven", "pom-tomcat.xml");
    HttpGetVerifier.verifyBody("Hello world", new URL("http://" + dockerHost + ":8080/hello"));
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
    assertThat(sizeOutput).contains(" /app/classpath/spring-boot-0.1.0.original.jar");
    int fileSize = Integer.parseInt(sizeOutput.substring(0, sizeOutput.indexOf(' ')));
    assertThat(fileSize).isLessThan(3000); // should not be a large fat jar

    HttpGetVerifier.verifyBody("Hello world", new URL("http://" + dockerHost + ":8080"));
  }

  @Test
  public void testExecute_multiPlatformBuild()
      throws IOException, VerificationException, RegistryException {
    String targetImage = dockerHost + ":5000/multiplatform:maven" + System.nanoTime();

    Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
    verifier.setSystemProperty("_TARGET_IMAGE", targetImage);

    verifier.setSystemProperty("jib.to.auth.username", "testuser");
    verifier.setSystemProperty("jib.to.auth.password", "testpassword");
    verifier.setSystemProperty("sendCredentialsOverHttp", "true");
    verifier.setSystemProperty("jib.allowInsecureRegistries", "true");

    verifier.setAutoclean(false);
    verifier.addCliOption("--file=pom-multiplatform-build.xml");
    verifier.executeGoals(Arrays.asList("clean", "compile", "jib:build"));
    verifier.verifyErrorFreeLog();

    FailoverHttpClient httpClient = new FailoverHttpClient(true, true, ignored -> {});
    RegistryClient registryClient =
        RegistryClient.factory(
                EventHandlers.NONE, dockerHost + ":5000", "multiplatform", httpClient)
            .setCredential(Credential.from("testuser", "testpassword"))
            .newRegistryClient();
    registryClient.configureBasicAuth();

    // manifest list by tag ":latest"
    ManifestTemplate manifestList = registryClient.pullManifest("latest").getManifest();
    assertThat(manifestList).isInstanceOf(V22ManifestListTemplate.class);
    V22ManifestListTemplate v22ManifestList = (V22ManifestListTemplate) manifestList;

    assertThat(v22ManifestList.getManifests().size()).isEqualTo(2);
    ManifestDescriptorTemplate.Platform platform1 =
        v22ManifestList.getManifests().get(0).getPlatform();
    ManifestDescriptorTemplate.Platform platform2 =
        v22ManifestList.getManifests().get(1).getPlatform();

    assertThat(platform1.getArchitecture()).isEqualTo("arm64");
    assertThat(platform1.getOs()).isEqualTo("linux");
    assertThat(platform2.getArchitecture()).isEqualTo("amd64");
    assertThat(platform2.getOs()).isEqualTo("linux");

    // manifest list by tag ":another"
    ManifestTemplate anotherManifestList = registryClient.pullManifest("another").getManifest();
    assertThat(JsonTemplateMapper.toUtf8String(anotherManifestList))
        .isEqualTo(JsonTemplateMapper.toUtf8String(manifestList));

    // Check arm64/linux container config.
    List<String> arm64Digests = v22ManifestList.getDigestsForPlatform("arm64", "linux");
    assertThat(arm64Digests.size()).isEqualTo(1);
    String arm64Digest = arm64Digests.get(0);

    ManifestTemplate arm64Manifest = registryClient.pullManifest(arm64Digest).getManifest();
    assertThat(arm64Manifest).isInstanceOf(V22ManifestTemplate.class);
    V22ManifestTemplate arm64V22Manifest = (V22ManifestTemplate) arm64Manifest;
    DescriptorDigest arm64ConfigDigest = arm64V22Manifest.getContainerConfiguration().getDigest();

    Blob arm64ConfigBlob = registryClient.pullBlob(arm64ConfigDigest, ignored -> {}, ignored -> {});
    String arm64Config = Blobs.writeToString(arm64ConfigBlob);
    assertThat(arm64Config).contains("\"architecture\":\"arm64\"");
    assertThat(arm64Config).contains("\"os\":\"linux\"");

    // Check amd64/linux container config.
    List<String> amd64Digests = v22ManifestList.getDigestsForPlatform("amd64", "linux");
    assertThat(amd64Digests.size()).isEqualTo(1);
    String amd64Digest = amd64Digests.get(0);

    ManifestTemplate amd64Manifest = registryClient.pullManifest(amd64Digest).getManifest();
    assertThat(amd64Manifest).isInstanceOf(V22ManifestTemplate.class);
    V22ManifestTemplate amd64V22Manifest = (V22ManifestTemplate) amd64Manifest;
    DescriptorDigest amd64ConfigDigest = amd64V22Manifest.getContainerConfiguration().getDigest();

    Blob amd64ConfigBlob = registryClient.pullBlob(amd64ConfigDigest, ignored -> {}, ignored -> {});
    String amd64Config = Blobs.writeToString(amd64ConfigBlob);
    assertThat(amd64Config).contains("\"architecture\":\"amd64\"");
    assertThat(amd64Config).contains("\"os\":\"linux\"");
  }

  private void buildAndRunWebApp(TestProject project, String label, String pomXml)
      throws VerificationException, IOException, InterruptedException {
    String targetImage = getTestImageReference(label);

    Verifier verifier = new Verifier(project.getProjectRoot().toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
    if (targetImage.startsWith(dockerHost)) {
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
