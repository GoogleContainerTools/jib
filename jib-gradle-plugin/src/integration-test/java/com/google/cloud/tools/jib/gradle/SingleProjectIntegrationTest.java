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

package com.google.cloud.tools.jib.gradle;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.IntegrationTestingConfiguration;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.registry.LocalRegistry;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import java.time.Instant;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Integration tests for building single project images. */
public class SingleProjectIntegrationTest {

  @ClassRule
  public static final LocalRegistry localRegistry1 =
      new LocalRegistry(5000, "testuser", "testpassword");

  @ClassRule
  public static final LocalRegistry localRegistry2 =
      new LocalRegistry(6000, "testuser2", "testpassword2");

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static boolean isJava11RuntimeOrHigher() {
    Iterable<String> split = Splitter.on(".").split(System.getProperty("java.version"));
    return Integer.valueOf(split.iterator().next()) >= 11;
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

  /**
   * Asserts that the test project has the required exposed ports, labels and volumes.
   *
   * @param imageReference the image to test
   * @throws IOException if the {@code docker inspect} command fails to run
   * @throws InterruptedException if the {@code docker inspect} command is interrupted
   */
  private static void assertDockerInspect(String imageReference)
      throws IOException, InterruptedException {
    String dockerInspect = new Command("docker", "inspect", imageReference).run();
    assertThat(dockerInspect)
        .contains(
            "            \"Volumes\": {\n"
                + "                \"/var/log\": {},\n"
                + "                \"/var/log2\": {}\n"
                + "            },");
    assertThat(dockerInspect)
        .contains(
            "            \"ExposedPorts\": {\n"
                + "                \"1000/tcp\": {},\n"
                + "                \"2000/udp\": {},\n"
                + "                \"2001/udp\": {},\n"
                + "                \"2002/udp\": {},\n"
                + "                \"2003/udp\": {}");
    assertThat(dockerInspect)
        .contains(
            "            \"Labels\": {\n"
                + "                \"key1\": \"value1\",\n"
                + "                \"key2\": \"value2\"\n"
                + "            }");
  }

  private static String readDigestFile(Path digestPath) throws IOException, DigestException {
    Assert.assertTrue(Files.exists(digestPath));
    String digest = new String(Files.readAllBytes(digestPath), StandardCharsets.UTF_8);
    return DescriptorDigest.fromDigest(digest).toString();
  }

  private static String buildAndRunComplex(
      String imageReference, String username, String password, LocalRegistry targetRegistry)
      throws IOException, InterruptedException {
    Path baseCache = simpleTestProject.getProjectRoot().resolve("build/jib-base-cache");
    BuildResult buildResult =
        simpleTestProject.build(
            "clean",
            "jib",
            "-Djib.baseImageCache=" + baseCache,
            "-Djib.console=plain",
            "-D_TARGET_IMAGE=" + imageReference,
            "-D_TARGET_USERNAME=" + username,
            "-D_TARGET_PASSWORD=" + password,
            "-DsendCredentialsOverHttp=true",
            "-b=complex-build.gradle");

    JibRunHelper.assertBuildSuccess(buildResult, "jib", "Built and pushed image as ");
    MatcherAssert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(imageReference));

    targetRegistry.pull(imageReference);
    assertDockerInspect(imageReference);
    String history = new Command("docker", "history", imageReference).run();
    MatcherAssert.assertThat(history, CoreMatchers.containsString("jib-gradle-plugin"));

    String output = new Command("docker", "run", "--rm", imageReference).run();
    Assert.assertEquals(
        "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrwxr-xr-x\nrwxrwxrwx\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n"
            + "-Xms512m\n-Xdebug\nenvvalue1\nenvvalue2\n",
        output);
    return output;
  }

  @Before
  public void setup() throws IOException, InterruptedException {
    // Pull distroless and push to local registry so we can test 'from' credentials
    localRegistry1.pullAndPushToLocal("gcr.io/distroless/java:latest", "distroless/java");
  }

  @Test
  public void testBuild_simple()
      throws IOException, InterruptedException, DigestException, InvalidImageReferenceException {
    String targetImage =
        IntegrationTestingConfiguration.getTestRepositoryLocation()
            + "/simpleimage:gradle"
            + System.nanoTime();

    // Test empty output error
    try {
      simpleTestProject.build(
          "clean",
          "jib",
          "-Djib.useOnlyProjectCache=true",
          "-Djib.console=plain",
          "-x=classes",
          "-D_TARGET_IMAGE=" + targetImage);
      Assert.fail();

    } catch (UnexpectedBuildFailure ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "No classes files were found - did you compile your project?"));
    }

    String output = JibRunHelper.buildAndRun(simpleTestProject, targetImage);

    Assert.assertEquals(
        "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n",
        output);

    String digest =
        readDigestFile(simpleTestProject.getProjectRoot().resolve("build/jib-image.digest"));
    String imageReferenceWithDigest =
        ImageReference.parse(targetImage).withQualifier(digest).toString();
    Assert.assertEquals(output, JibRunHelper.pullAndRunBuiltImage(imageReferenceWithDigest));

    String id = readDigestFile(simpleTestProject.getProjectRoot().resolve("build/jib-image.id"));
    Assert.assertNotEquals(digest, id);
    Assert.assertEquals(output, new Command("docker", "run", "--rm", id).run());

    assertDockerInspect(targetImage);
    assertThat(JibRunHelper.getCreationTime(targetImage)).isEqualTo(Instant.EPOCH);
    assertThat(getWorkingDirectory(targetImage)).isEqualTo("/home");
    assertThat(getEntrypoint(targetImage))
        .isEqualTo(
            "[java -cp /d1:/d2:/app/resources:/app/classes:/app/libs/* com.test.HelloWorld]");
    assertThat(getLayerSize(targetImage)).isEqualTo(9);
  }

  @Test
  public void testBuild_dockerDaemonBase()
      throws IOException, InterruptedException, DigestException {
    String targetImage =
        IntegrationTestingConfiguration.getTestRepositoryLocation()
            + "/simplewithdockerdaemonbase:gradle"
            + System.nanoTime();
    Assert.assertEquals(
        "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n",
        JibRunHelper.buildAndRunFromLocalBase(
            targetImage, "docker://gcr.io/distroless/java:latest"));
  }

  @Test
  public void testBuild_tarBase() throws IOException, InterruptedException, DigestException {
    Path path = temporaryFolder.getRoot().toPath().resolve("docker-save-distroless");
    new Command("docker", "save", "gcr.io/distroless/java:latest", "-o", path.toString()).run();
    String targetImage =
        IntegrationTestingConfiguration.getTestRepositoryLocation()
            + "/simplewithtarbase:gradle"
            + System.nanoTime();
    Assert.assertEquals(
        "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n",
        JibRunHelper.buildAndRunFromLocalBase(targetImage, "tar://" + path));
  }

  @Test
  public void testBuild_failOffline() {
    String targetImage =
        IntegrationTestingConfiguration.getTestRepositoryLocation()
            + "/simpleimageoffline:gradle"
            + System.nanoTime();

    try {
      simpleTestProject.build(
          "--offline",
          "clean",
          "jib",
          "-Djib.useOnlyProjectCache=true",
          "-Djib.console=plain",
          "-D_TARGET_IMAGE=" + targetImage);
      Assert.fail();
    } catch (UnexpectedBuildFailure ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString("Cannot build to a container registry in offline mode"));
    }
  }

  @Test
  public void testDockerDaemon_simpleOnJava11()
      throws DigestException, IOException, InterruptedException {
    Assume.assumeTrue(isJava11RuntimeOrHigher());

    String targetImage = "simpleimage:gradle" + System.nanoTime();
    Assert.assertEquals(
        "Hello, world. \n1970-01-01T00:00:01Z\n",
        JibRunHelper.buildToDockerDaemonAndRun(
            simpleTestProject, targetImage, "build-java11.gradle"));
  }

  @Test
  public void testDockerDaemon_simpleWithIncompatibleJava11()
      throws DigestException, IOException, InterruptedException {
    Assume.assumeTrue(isJava11RuntimeOrHigher());

    try {
      JibRunHelper.buildToDockerDaemonAndRun(
          simpleTestProject, "willnotbuild", "build-java11-incompatible.gradle");
      Assert.fail();

    } catch (UnexpectedBuildFailure ex) {
      MatcherAssert.assertThat(
          ex.getMessage(),
          CoreMatchers.containsString(
              "Your project is using Java 11 but the base image is for Java 8, perhaps you should "
                  + "configure a Java 11-compatible base image using the 'jib.from.image' "
                  + "parameter, or set targetCompatibility = 8 or below in your build "
                  + "configuration"));
    }
  }

  @Test
  public void testDockerDaemon_simple_multipleExtraDirectories()
      throws DigestException, IOException, InterruptedException {
    String targetImage = "simpleimage:gradle" + System.nanoTime();
    Assert.assertEquals(
        "Hello, world. \n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n",
        JibRunHelper.buildToDockerDaemonAndRun(
            simpleTestProject, targetImage, "build-extra-dirs.gradle"));
    assertThat(getLayerSize(targetImage)).isEqualTo(10); // one more than usual
  }

  @Test
  public void testDockerDaemon_simple_multipleExtraDirectoriesWithAlternativeConfig()
      throws DigestException, IOException, InterruptedException {
    String targetImage = "simpleimage:gradle" + System.nanoTime();
    Assert.assertEquals(
        "Hello, world. \n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n",
        JibRunHelper.buildToDockerDaemonAndRun(
            simpleTestProject, targetImage, "build-extra-dirs2.gradle"));
    assertThat(getLayerSize(targetImage)).isEqualTo(10); // one more than usual
  }

  @Test
  public void testDockerDaemon_simple_multipleExtraDirectoriesWithClosure()
      throws DigestException, IOException, InterruptedException {
    String targetImage = "simpleimage:gradle" + System.nanoTime();
    Assert.assertEquals(
        "Hello, world. \n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\nbaz\n1970-01-01T00:00:01Z\n",
        JibRunHelper.buildToDockerDaemonAndRun(
            simpleTestProject, targetImage, "build-extra-dirs3.gradle"));
    assertThat(getLayerSize(targetImage)).isEqualTo(10); // one more than usual
  }

  @Test
  public void testDockerDaemon_simple_extraDirectoriesFiltering()
      throws DigestException, IOException, InterruptedException {
    String targetImage = "simpleimage:gradle" + System.nanoTime();
    JibRunHelper.buildToDockerDaemon(
        simpleTestProject, targetImage, "build-extra-dirs-filtering.gradle");
    String output =
        new Command("docker", "run", "--rm", "--entrypoint=ls", targetImage, "-1R", "/extras")
            .run();

    // No "bar" or "*.txt" files. Only copies the following:
    //   /extras/cat.json
    //   /extras/foo
    //   /extras/sub/
    //   /extras/sub/a.json
    assertThat(output).isEqualTo("/extras:\ncat.json\nfoo\nsub\n\n/extras/sub:\na.json\n");
  }

  @Test
  public void testBuild_complex()
      throws IOException, InterruptedException, DigestException, InvalidImageReferenceException {
    String targetImage = "localhost:6000/compleximage:gradle" + System.nanoTime();
    Instant beforeBuild = Instant.now();
    String output = buildAndRunComplex(targetImage, "testuser2", "testpassword2", localRegistry2);

    String digest =
        readDigestFile(
            simpleTestProject.getProjectRoot().resolve("build/different-jib-image.digest"));
    String imageReferenceWithDigest =
        ImageReference.parse(targetImage).withQualifier(digest).toString();
    localRegistry2.pull(imageReferenceWithDigest);
    Assert.assertEquals(
        output, new Command("docker", "run", "--rm", imageReferenceWithDigest).run());

    String id =
        readDigestFile(simpleTestProject.getProjectRoot().resolve("different-jib-image.id"));
    Assert.assertNotEquals(digest, id);
    Assert.assertEquals(output, new Command("docker", "run", "--rm", id).run());

    assertThat(JibRunHelper.getCreationTime(targetImage)).isGreaterThan(beforeBuild);
    assertThat(getWorkingDirectory(targetImage)).isEqualTo("/");
  }

  @Test
  public void testBuild_complex_sameFromAndToRegistry() throws IOException, InterruptedException {
    String targetImage = "localhost:5000/compleximage:gradle" + System.nanoTime();
    Instant beforeBuild = Instant.now();
    buildAndRunComplex(targetImage, "testuser", "testpassword", localRegistry1);
    assertThat(JibRunHelper.getCreationTime(targetImage)).isGreaterThan(beforeBuild);
    assertThat(getWorkingDirectory(targetImage)).isEqualTo("/");
  }

  @Test
  public void testDockerDaemon_simple() throws IOException, InterruptedException, DigestException {
    String targetImage = "simpleimage:gradle" + System.nanoTime();
    Assert.assertEquals(
        "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n",
        JibRunHelper.buildToDockerDaemonAndRun(simpleTestProject, targetImage, "build.gradle"));
    assertThat(JibRunHelper.getCreationTime(targetImage)).isEqualTo(Instant.EPOCH);
    assertDockerInspect(targetImage);
    assertThat(getWorkingDirectory(targetImage)).isEqualTo("/home");
  }

  @Test
  public void testDockerDaemon_jarContainerization()
      throws DigestException, IOException, InterruptedException {
    String targetImage = "simpleimage:gradle" + System.nanoTime();
    Assert.assertEquals(
        "Hello, world. \nImplementation-Title: helloworld\nImplementation-Version: 1\n",
        JibRunHelper.buildToDockerDaemonAndRun(
            simpleTestProject, targetImage, "build-jar-containerization.gradle"));
  }

  @Test
  public void testBuild_skipDownloadingBaseImageLayers() throws IOException, InterruptedException {
    Path baseLayersCacheDirectory =
        simpleTestProject.getProjectRoot().resolve("build/jib-base-cache/layers");
    String targetImage = "localhost:6000/simpleimage:gradle" + System.nanoTime();

    buildAndRunComplex(targetImage, "testuser2", "testpassword2", localRegistry2);
    // Base image layer tarballs exist.
    Assert.assertTrue(Files.exists(baseLayersCacheDirectory));
    Assert.assertTrue(baseLayersCacheDirectory.toFile().list().length >= 2);

    buildAndRunComplex(targetImage, "testuser2", "testpassword2", localRegistry2);
    // no base layers downloaded after "gradle clean jib ..."
    Assert.assertFalse(Files.exists(baseLayersCacheDirectory));
  }

  @Test
  public void testDockerDaemon_timestampCustom()
      throws DigestException, IOException, InterruptedException {
    String targetImage = "simpleimage:gradle" + System.nanoTime();
    Assert.assertEquals(
        "Hello, world. \n2011-12-03T01:15:30Z\n",
        JibRunHelper.buildToDockerDaemonAndRun(
            simpleTestProject, targetImage, "build-timestamps-custom.gradle"));
    assertThat(JibRunHelper.getCreationTime(targetImage))
        .isEqualTo(Instant.parse("2013-11-04T21:29:30Z"));
  }

  @Test
  public void testBuild_dockerClient() throws IOException, InterruptedException, DigestException {
    Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
    new Command(
            "chmod", "+x", simpleTestProject.getProjectRoot().resolve("mock-docker.sh").toString())
        .run();

    String targetImage = "simpleimage:gradle" + System.nanoTime();

    BuildResult buildResult =
        simpleTestProject.build(
            "clean",
            "jibDockerBuild",
            "-Djib.useOnlyProjectCache=true",
            "-Djib.console=plain",
            "-D_TARGET_IMAGE=" + targetImage,
            "-b=build-dockerclient.gradle",
            "--debug");
    JibRunHelper.assertBuildSuccess(
        buildResult, "jibDockerBuild", "Built image to Docker daemon as ");
    JibRunHelper.assertImageDigestAndId(simpleTestProject.getProjectRoot());
    MatcherAssert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(targetImage));
    MatcherAssert.assertThat(
        buildResult.getOutput(), CoreMatchers.containsString("Docker load called. value1 value2"));
  }

  @Test
  public void testBuildTar_simple() throws IOException, InterruptedException {
    String targetImage = "simpleimage:gradle" + System.nanoTime();

    String outputPath =
        simpleTestProject
            .getProjectRoot()
            .resolve("build")
            .resolve("different-jib-image.tar")
            .toString();
    BuildResult buildResult =
        simpleTestProject.build(
            "clean",
            "jibBuildTar",
            "-Djib.useOnlyProjectCache=true",
            "-Djib.console=plain",
            "-D_TARGET_IMAGE=" + targetImage);

    JibRunHelper.assertBuildSuccess(buildResult, "jibBuildTar", "Built image tarball at ");
    MatcherAssert.assertThat(buildResult.getOutput(), CoreMatchers.containsString(outputPath));

    new Command("docker", "load", "--input", outputPath).run();
    Assert.assertEquals(
        "Hello, world. An argument.\n1970-01-01T00:00:01Z\nrw-r--r--\nrw-r--r--\nfoo\ncat\n"
            + "1970-01-01T00:00:01Z\n1970-01-01T00:00:01Z\n",
        new Command("docker", "run", "--rm", targetImage).run());
    assertDockerInspect(targetImage);
    assertThat(JibRunHelper.getCreationTime(targetImage)).isEqualTo(Instant.EPOCH);
    assertThat(getWorkingDirectory(targetImage)).isEqualTo("/home");
  }
}
