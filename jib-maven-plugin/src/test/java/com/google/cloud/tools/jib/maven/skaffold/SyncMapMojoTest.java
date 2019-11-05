package com.google.cloud.tools.jib.maven.skaffold;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.maven.TestProject;
import com.google.cloud.tools.jib.plugins.common.SkaffoldSyncMapTemplate;
import com.google.cloud.tools.jib.plugins.common.SkaffoldSyncMapTemplate.FileTemplate;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
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

  private static String getSyncMapJson(Path projectRoot, String module)
      throws VerificationException, IOException {
    Verifier verifier = new Verifier(projectRoot.toString());
    verifier.setAutoclean(false);
    verifier.addCliOption("-q");
    if (!Strings.isNullOrEmpty(module)) {
      verifier.addCliOption("-pl");
      verifier.addCliOption(module);
      verifier.addCliOption("-am");
      verifier.addCliOption("-Djib.containerize=com.jib.test:" + module);
    }
    verifier.addCliOption("-DskipTests");
    verifier.executeGoals(ImmutableList.of("package", "jib:" + SyncMapMojo.GOAL_NAME));

    Path logFile = Paths.get(verifier.getBasedir()).resolve(verifier.getLogFileName());
    List<String> outputLines = Files.readAllLines(logFile, Charsets.UTF_8);
    Assert.assertEquals(3, outputLines.size()); // we expect ["\n", "BEGIN JIB JSON", "<sync-json>"]
    return outputLines.get(2); // this is the JSON output
  }

  private static void assertFilePaths(Path src, AbsoluteUnixPath dest, FileTemplate template) {
    Assert.assertEquals(src.toString(), template.getSrc());
    Assert.assertEquals(dest.toString(), template.getDest());
  }

  @Test
  public void testSyncMapMojo_simpleTestProjectOutput() throws IOException, VerificationException {
    Path projectRoot = simpleTestProject.getProjectRoot();
    String json = getSyncMapJson(projectRoot, null);
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
    String json = getSyncMapJson(projectRoot, "complex-service");
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
}
