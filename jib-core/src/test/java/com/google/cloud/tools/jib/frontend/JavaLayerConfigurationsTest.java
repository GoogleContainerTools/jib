package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations.Builder;
import com.google.cloud.tools.jib.image.LayerEntry;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link JavaLayerConfigurations}. */
public class JavaLayerConfigurationsTest {

  private static JavaLayerConfigurations createFakeConfigurations(String appRoot)
      throws IOException {
    return JavaLayerConfigurations.builder()
        .setAppRoot(appRoot)
        .setDependencyFiles(Collections.singletonList(Paths.get("dependency")))
        .setSnapshotDependencyFiles(Collections.singletonList(Paths.get("snapshot dependency")))
        .setResourceFiles(Collections.singletonList(Paths.get("resource")))
        .setClassFiles(Collections.singletonList(Paths.get("class")))
        .setExplodedWarFiles(Collections.singletonList(Paths.get("exploded war")))
        .setExtraFiles(Collections.singletonList(Paths.get("extra file")))
        .build();
  }

  private static void verifyExtractionPath(JavaLayerConfigurations configurations, String appRoot) {
    Assert.assertEquals(
        Paths.get(
            appRoot + JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE + "/dependency"),
        configurations.getDependencyLayerEntries().get(0).getExtractionPath());
    Assert.assertEquals(
        Paths.get(
            appRoot
                + JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE
                + "/snapshot dependency"),
        configurations.getSnapshotDependencyLayerEntries().get(0).getExtractionPath());
    Assert.assertEquals(
        Paths.get(
            appRoot + JavaEntrypointConstructor.DEFAULT_RESOURCES_PATH_ON_IMAGE + "/resource"),
        configurations.getResourceLayerEntries().get(0).getExtractionPath());
    Assert.assertEquals(
        Paths.get(appRoot + JavaEntrypointConstructor.DEFAULT_CLASSES_PATH_ON_IMAGE + "/class"),
        configurations.getClassLayerEntries().get(0).getExtractionPath());
    Assert.assertEquals(
        Paths.get(appRoot + "exploded war"),
        configurations.getExplodedWarEntries().get(0).getExtractionPath());
    Assert.assertEquals(
        Paths.get("/extra file"),
        configurations.getExtraFilesLayerEntries().get(0).getExtractionPath());
  }

  private static List<Path> layerEntriesToSourceFiles(List<LayerEntry> entries) {
    return entries.stream().map(LayerEntry::getSourceFile).collect(Collectors.toList());
  }

  @Test
  public void testDefault() throws IOException {
    JavaLayerConfigurations javaLayerConfigurations =
        createFakeConfigurations(ContainerConfiguration.DEFAULT_APP_ROOT);

    verifyExtractionPath(javaLayerConfigurations, "/app/");

    List<String> expectedLabels = new ArrayList<>();
    for (JavaLayerConfigurations.LayerType layerType : JavaLayerConfigurations.LayerType.values()) {
      expectedLabels.add(layerType.getName());
    }
    List<String> actualLabels = new ArrayList<>();
    for (LayerConfiguration layerConfiguration : javaLayerConfigurations.getLayerConfigurations()) {
      actualLabels.add(layerConfiguration.getName());
    }
    Assert.assertEquals(expectedLabels, actualLabels);
  }

  @Test
  public void testSetFiles() throws IOException {
    List<Path> dependencyFiles = Collections.singletonList(Paths.get("dependency"));
    List<Path> snapshotDependencyFiles =
        Collections.singletonList(Paths.get("snapshot dependency"));
    List<Path> resourceFiles = Collections.singletonList(Paths.get("resource"));
    List<Path> classFiles = Collections.singletonList(Paths.get("class"));
    List<Path> explodedWarFiles = Collections.singletonList(Paths.get("exploded war"));
    List<Path> extraFiles = Collections.singletonList(Paths.get("extra file"));

    JavaLayerConfigurations javaLayerConfigurations = createFakeConfigurations("/whatever");

    List<List<Path>> expectedFiles =
        Arrays.asList(
            dependencyFiles,
            snapshotDependencyFiles,
            resourceFiles,
            classFiles,
            explodedWarFiles,
            extraFiles);
    List<List<Path>> actualFiles =
        javaLayerConfigurations
            .getLayerConfigurations()
            .stream()
            .map(LayerConfiguration::getLayerEntries)
            .map(JavaLayerConfigurationsTest::layerEntriesToSourceFiles)
            .collect(Collectors.toList());
    Assert.assertEquals(expectedFiles, actualFiles);
  }

  @Test
  public void testGetAppRoot() throws IOException {
    Assert.assertEquals(
        "/my/app/root",
        JavaLayerConfigurations.builder().setAppRoot("/my/app/root").build().getAppRoot());
  }

  @Test
  public void testSetAppRoot_nonAbsolute() throws IOException {
    Builder appRoot = JavaLayerConfigurations.builder().setAppRoot("relative/path");
    try {
      appRoot.build();
      Assert.fail();
    } catch (IllegalStateException ex) {
      Assert.assertEquals("appRoot should be an absolute path in Unix-style", ex.getMessage());
    }
  }

  @Test
  public void testSetAppRoot_windowsPath() throws IOException {
    Builder appRoot = JavaLayerConfigurations.builder().setAppRoot("\\windows\\path");
    try {
      appRoot.build();
      Assert.fail();
    } catch (IllegalStateException ex) {
      Assert.assertEquals("appRoot should be an absolute path in Unix-style", ex.getMessage());
    }
  }

  @Test
  public void testSetAppRoot_windowsPathWithDriveLetter() throws IOException {
    Builder appRoot = JavaLayerConfigurations.builder().setAppRoot("D:\\windows\\path");
    try {
      appRoot.build();
      Assert.fail();
    } catch (IllegalStateException ex) {
      Assert.assertEquals("appRoot should be an absolute path in Unix-style", ex.getMessage());
    }
  }

  @Test
  public void testExtractionPath_nonDefaultAppRoot() throws IOException {
    JavaLayerConfigurations configurations = createFakeConfigurations("/my/app/root");
    verifyExtractionPath(configurations, "/my/app/root/");
  }
}
