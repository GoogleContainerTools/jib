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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.maven.TestProject;
import com.google.cloud.tools.jib.plugins.common.SkaffoldSyncMapTemplate;
import com.google.cloud.tools.jib.plugins.common.SkaffoldSyncMapTemplate.FileTemplate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Tests for {@link SyncMapMojo}. */
public class SyncMapMojoTest {

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");
  @ClassRule public static final TestProject multiTestProject = new TestProject("multi");
  @ClassRule public static final TestProject warProject = new TestProject("war_servlet25");

  private static Path runBuild(Path projectRoot, String module, String pomXml)
      throws VerificationException {
    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.addCliOption("-q");
    if (pomXml != null) {
      verifier.addCliOption("--file=" + pomXml);
    }
    if (!Strings.isNullOrEmpty(module)) {
      verifier.addCliOption("-pl");
      verifier.addCliOption(module);
      verifier.addCliOption("-am");
      verifier.addCliOption("-Djib.containerize=com.jib.test:" + module);
    }
    verifier.addCliOption("-DskipTests");
    verifier.executeGoals(ImmutableList.of("package", "jib:" + SyncMapMojo.GOAL_NAME));

    return Paths.get(verifier.getBasedir()).resolve(verifier.getLogFileName());
  }

  private static String getSyncMapJson(Path projectRoot, String module, String pomXml)
      throws VerificationException, IOException {
    Path logFile = runBuild(projectRoot, module, pomXml);
    List<String> outputLines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
    Assert.assertEquals(3, outputLines.size()); // we expect ["\n", "<marker>", "<sync-json>"]
    Assert.assertEquals("BEGIN JIB JSON: SYNCMAP/1", outputLines.get(1));
    return outputLines.get(2); // this is the JSON output
  }

  private static void assertFilePaths(Path src, AbsoluteUnixPath dest, FileTemplate template) {
    Assert.assertEquals(src.toString(), template.getSrc());
    Assert.assertEquals(dest.toString(), template.getDest());
  }

  @Test
  public void testSyncMapMojo_simpleTestProjectOutput() throws IOException, VerificationException {
    Path projectRoot = simpleTestProject.getProjectRoot();
    String json = getSyncMapJson(projectRoot, null, null);
    SkaffoldSyncMapTemplate parsed = SkaffoldSyncMapTemplate.from(json);

    List<FileTemplate> generated = parsed.getGenerated();
    Assert.assertEquals(2, generated.size());
    assertFilePaths(
        projectRoot.resolve("target/classes/world"),
        AbsoluteUnixPath.get("/app/resources/world"),
        generated.get(0));
    assertFilePaths(
        projectRoot.resolve("target/classes/com/test/HelloWorld.class"),
        AbsoluteUnixPath.get("/app/classes/com/test/HelloWorld.class"),
        generated.get(1));

    List<FileTemplate> direct = parsed.getDirect();
    Assert.assertEquals(2, direct.size());
    assertFilePaths(
        projectRoot.resolve("src/main/jib-custom/bar/cat"),
        AbsoluteUnixPath.get("/bar/cat"),
        direct.get(0));
    assertFilePaths(
        projectRoot.resolve("src/main/jib-custom/foo"),
        AbsoluteUnixPath.get("/foo"),
        direct.get(1));
  }

  @Test
  public void testSyncMapMojo_multiProjectOutput() throws IOException, VerificationException {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path m2 = Paths.get(System.getProperty("user.home")).resolve(".m2").resolve("repository");
    String json = getSyncMapJson(projectRoot, "complex-service", null);
    SkaffoldSyncMapTemplate parsed = SkaffoldSyncMapTemplate.from(json);

    List<FileTemplate> generated = parsed.getGenerated();
    Assert.assertEquals(2, generated.size());
    assertFilePaths(
        projectRoot.resolve("lib/target/lib-1.0.0.TEST-SNAPSHOT.jar"),
        AbsoluteUnixPath.get("/app/libs/lib-1.0.0.TEST-SNAPSHOT.jar"),
        generated.get(0));
    assertFilePaths(
        projectRoot.resolve("complex-service/target/classes/com/test/HelloWorld.class"),
        AbsoluteUnixPath.get("/app/classes/com/test/HelloWorld.class"),
        generated.get(1));

    List<FileTemplate> direct = parsed.getDirect();
    Assert.assertEquals(1, direct.size());
    assertFilePaths(
        m2.resolve(
            "com/google/cloud/tools/tiny-test-lib/0.0.1-SNAPSHOT/tiny-test-lib-0.0.1-SNAPSHOT.jar"),
        AbsoluteUnixPath.get("/app/libs/tiny-test-lib-0.0.1-SNAPSHOT.jar"),
        direct.get(0));
  }

  @Test
  public void testSyncMapMojo_skaffoldConfig() throws IOException, VerificationException {
    Path projectRoot = simpleTestProject.getProjectRoot();
    String json = getSyncMapJson(projectRoot, null, "pom-skaffold-config.xml");
    SkaffoldSyncMapTemplate parsed = SkaffoldSyncMapTemplate.from(json);

    List<FileTemplate> generated = parsed.getGenerated();
    Assert.assertEquals(1, generated.size());
    assertFilePaths(
        projectRoot.resolve("target/classes/world"),
        AbsoluteUnixPath.get("/app/resources/world"),
        generated.get(0));
    // target/classes/com/test ignored

    List<FileTemplate> direct = parsed.getDirect();
    Assert.assertEquals(1, direct.size());
    assertFilePaths(
        projectRoot.resolve("src/main/jib-custom/bar/cat"),
        AbsoluteUnixPath.get("/bar/cat"),
        direct.get(0));
    // src/main/jib-custom/foo is ignored
  }

  @Test
  public void testSyncMapMojo_failIfPackagingNotJar() throws IOException {
    Path projectRoot = warProject.getProjectRoot();
    VerificationException ve =
        assertThrows(VerificationException.class, () -> runBuild(projectRoot, null, null));
    assertThat(ve)
        .hasMessageThat()
        .contains(
            "MojoExecutionException: Skaffold sync is currently only available for 'jar' style Jib "
                + "projects, but the packaging of servlet25 is 'war'");
  }

  @Test
  public void testSyncMapMojo_failIfJarContainerizationMode() throws IOException {
    Path projectRoot = simpleTestProject.getProjectRoot();
    VerificationException ve =
        assertThrows(
            VerificationException.class,
            () -> runBuild(projectRoot, null, "pom-jar-containerization.xml"));
    assertThat(ve)
        .hasMessageThat()
        .contains(
            "MojoExecutionException: Skaffold sync is currently only available for Jib projects in "
                + "'exploded' containerizing mode, but the containerizing mode of hello-world is "
                + "'packaged'");
  }
}
