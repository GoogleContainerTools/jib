package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations.LayerType;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link JavaLayerConfigurations}. */
public class JavaLayerConfigurationsTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static <T> void assertLayerEntriesUnordered(
      List<T> expectedPaths, List<LayerEntry> entries, Function<LayerEntry, T> fieldSelector) {
    List<T> expected = expectedPaths.stream().sorted().collect(Collectors.toList());
    List<T> actual = entries.stream().map(fieldSelector).sorted().collect(Collectors.toList());
    Assert.assertEquals(expected, actual);
  }

  private static void assertSourcePathsUnordered(
      List<Path> expectedPaths, List<LayerEntry> entries) {
    assertLayerEntriesUnordered(expectedPaths, entries, LayerEntry::getSourceFile);
  }

  private static void assertExtractionPathsUnordered(
      List<String> expectedPaths, List<LayerEntry> entries) {
    assertLayerEntriesUnordered(
        expectedPaths, entries, LayerEntry::getAbsoluteExtractionPathString);
  }

  private static JavaLayerConfigurations createFakeConfigurations() {
    return JavaLayerConfigurations.builder()
        .addFile(
            LayerType.DEPENDENCIES,
            Paths.get("dependency"),
            AbsoluteUnixPath.get("/dependency/path"))
        .addFile(
            LayerType.SNAPSHOT_DEPENDENCIES,
            Paths.get("snapshot dependency"),
            AbsoluteUnixPath.get("/snapshots"))
        .addFile(
            LayerType.RESOURCES, Paths.get("resource"), AbsoluteUnixPath.get("/resources/here"))
        .addFile(LayerType.CLASSES, Paths.get("class"), AbsoluteUnixPath.get("/classes/go/here"))
        .addFile(
            LayerType.EXTRA_FILES, Paths.get("extra file"), AbsoluteUnixPath.get("/some/extras"))
        .build();
  }

  @Test
  public void testLabels() {
    JavaLayerConfigurations javaLayerConfigurations = createFakeConfigurations();

    List<String> expectedLabels = new ArrayList<>();
    for (LayerType layerType : LayerType.values()) {
      expectedLabels.add(layerType.getName());
    }
    List<String> actualLabels = new ArrayList<>();
    for (LayerConfiguration layerConfiguration : javaLayerConfigurations.getLayerConfigurations()) {
      actualLabels.add(layerConfiguration.getName());
    }
    Assert.assertEquals(expectedLabels, actualLabels);
  }

  @Test
  public void testAddFile() {
    JavaLayerConfigurations javaLayerConfigurations = createFakeConfigurations();

    Assert.assertEquals(
        Arrays.asList(
            new LayerEntry(Paths.get("dependency"), AbsoluteUnixPath.get("/dependency/path"))),
        javaLayerConfigurations.getDependencyLayerEntries());
    Assert.assertEquals(
        Arrays.asList(
            new LayerEntry(Paths.get("snapshot dependency"), AbsoluteUnixPath.get("/snapshots"))),
        javaLayerConfigurations.getSnapshotDependencyLayerEntries());
    Assert.assertEquals(
        Arrays.asList(
            new LayerEntry(Paths.get("resource"), AbsoluteUnixPath.get("/resources/here"))),
        javaLayerConfigurations.getResourceLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(Paths.get("class"), AbsoluteUnixPath.get("/classes/go/here"))),
        javaLayerConfigurations.getClassLayerEntries());
    Assert.assertEquals(
        Arrays.asList(
            new LayerEntry(Paths.get("extra file"), AbsoluteUnixPath.get("/some/extras"))),
        javaLayerConfigurations.getExtraFilesLayerEntries());
  }

  @Test
  public void testAddFile_directories() throws URISyntaxException {
    Path sourceDirectory = Paths.get(Resources.getResource("random-contents").toURI());

    JavaLayerConfigurations configurations =
        JavaLayerConfigurations.builder()
            .addFile(LayerType.DEPENDENCIES, sourceDirectory, AbsoluteUnixPath.get("/libs/dir"))
            .addFile(
                LayerType.SNAPSHOT_DEPENDENCIES,
                sourceDirectory,
                AbsoluteUnixPath.get("/snapshots/target"))
            .addFile(LayerType.RESOURCES, sourceDirectory, AbsoluteUnixPath.get("/resources"))
            .addFile(LayerType.CLASSES, sourceDirectory, AbsoluteUnixPath.get("/classes/here"))
            .addFile(LayerType.EXTRA_FILES, sourceDirectory, AbsoluteUnixPath.get("/extra/files"))
            .build();

    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceDirectory, AbsoluteUnixPath.get("/libs/dir"))),
        configurations.getDependencyLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceDirectory, AbsoluteUnixPath.get("/snapshots/target"))),
        configurations.getSnapshotDependencyLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceDirectory, AbsoluteUnixPath.get("/resources"))),
        configurations.getResourceLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceDirectory, AbsoluteUnixPath.get("/classes/here"))),
        configurations.getClassLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceDirectory, AbsoluteUnixPath.get("/extra/files"))),
        configurations.getExtraFilesLayerEntries());
  }

  @Test
  public void testAddFile_regularFiles() throws URISyntaxException {
    Path sourceFile =
        Paths.get(Resources.getResource("random-contents/sub-directory/leaf/file6").toURI());

    JavaLayerConfigurations configurations =
        JavaLayerConfigurations.builder()
            .addFile(LayerType.DEPENDENCIES, sourceFile, AbsoluteUnixPath.get("/libs/file"))
            .addFile(
                LayerType.SNAPSHOT_DEPENDENCIES,
                sourceFile,
                AbsoluteUnixPath.get("/snapshots/target/file"))
            .addFile(LayerType.RESOURCES, sourceFile, AbsoluteUnixPath.get("/resources-file"))
            .addFile(LayerType.CLASSES, sourceFile, AbsoluteUnixPath.get("/classes/file"))
            .addFile(LayerType.EXTRA_FILES, sourceFile, AbsoluteUnixPath.get("/some/file"))
            .build();

    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceFile, AbsoluteUnixPath.get("/libs/file"))),
        configurations.getDependencyLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceFile, AbsoluteUnixPath.get("/snapshots/target/file"))),
        configurations.getSnapshotDependencyLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceFile, AbsoluteUnixPath.get("/resources-file"))),
        configurations.getResourceLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceFile, AbsoluteUnixPath.get("/classes/file"))),
        configurations.getClassLayerEntries());
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceFile, AbsoluteUnixPath.get("/some/file"))),
        configurations.getExtraFilesLayerEntries());
  }

  @Test
  public void testAddFile_webAppSample() {
    AbsoluteUnixPath appRoot = AbsoluteUnixPath.get("/usr/local/tomcat/webapps/ROOT/");

    JavaLayerConfigurations configurations =
        JavaLayerConfigurations.builder()
            .addFile(LayerType.RESOURCES, Paths.get("test.jsp"), appRoot.resolve("test.jsp"))
            .addFile(LayerType.RESOURCES, Paths.get("META-INF/"), appRoot.resolve("META-INF/"))
            .addFile(
                LayerType.RESOURCES,
                Paths.get("context.xml"),
                appRoot.resolve("WEB-INF/context.xml"))
            .addFile(
                LayerType.RESOURCES, Paths.get("sub_dir/"), appRoot.resolve("WEB-INF/sub_dir/"))
            .addFile(
                LayerType.DEPENDENCIES,
                Paths.get("myLib.jar"),
                appRoot.resolve("WEB-INF/lib/myLib.jar"))
            .addFile(
                LayerType.SNAPSHOT_DEPENDENCIES,
                Paths.get("my-SNAPSHOT.jar"),
                appRoot.resolve("WEB-INF/lib/my-SNAPSHOT.jar"))
            .addFile(
                LayerType.CLASSES,
                Paths.get("test.class"),
                appRoot.resolve("WEB-INF/classes/test.class"))
            .addFile(
                LayerType.EXTRA_FILES, Paths.get("extra.file"), AbsoluteUnixPath.get("/extra.file"))
            .build();

    ImmutableList<LayerEntry> expectedDependenciesLayer =
        ImmutableList.of(
            new LayerEntry(Paths.get("myLib.jar"), appRoot.resolve("WEB-INF/lib/myLib.jar")));
    ImmutableList<LayerEntry> expectedSnapshotDependenciesLayer =
        ImmutableList.of(
            new LayerEntry(
                Paths.get("my-SNAPSHOT.jar"), appRoot.resolve("WEB-INF/lib/my-SNAPSHOT.jar")));
    ImmutableList<LayerEntry> expectedResourcesLayer =
        ImmutableList.of(
            new LayerEntry(Paths.get("test.jsp"), appRoot.resolve("test.jsp")),
            new LayerEntry(Paths.get("META-INF"), appRoot.resolve("META-INF")),
            new LayerEntry(Paths.get("context.xml"), appRoot.resolve("WEB-INF/context.xml")),
            new LayerEntry(Paths.get("sub_dir"), appRoot.resolve("WEB-INF/sub_dir")));
    ImmutableList<LayerEntry> expectedClassesLayer =
        ImmutableList.of(
            new LayerEntry(Paths.get("test.class"), appRoot.resolve("WEB-INF/classes/test.class")));
    ImmutableList<LayerEntry> expectedExtraLayer =
        ImmutableList.of(
            new LayerEntry(Paths.get("extra.file"), AbsoluteUnixPath.get("/extra.file")));

    Assert.assertEquals(expectedDependenciesLayer, configurations.getDependencyLayerEntries());
    Assert.assertEquals(
        expectedSnapshotDependenciesLayer, configurations.getSnapshotDependencyLayerEntries());
    Assert.assertEquals(expectedResourcesLayer, configurations.getResourceLayerEntries());
    Assert.assertEquals(expectedClassesLayer, configurations.getClassLayerEntries());
    Assert.assertEquals(expectedExtraLayer, configurations.getExtraFilesLayerEntries());
  }

  @Test
  public void testAddDirectoryContents_file() throws IOException {
    temporaryFolder.newFile("file");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/path/in/container");

    JavaLayerConfigurations configurations =
        JavaLayerConfigurations.builder()
            .addDirectoryContents(LayerType.EXTRA_FILES, sourceRoot, path -> true, basePath)
            .build();
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceRoot.resolve("file"), basePath.resolve("file"))),
        configurations.getExtraFilesLayerEntries());
  }

  @Test
  public void testAddDirectoryContents_emptyDirectory() throws IOException {
    temporaryFolder.newFolder("leaf");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/");

    JavaLayerConfigurations configurations =
        JavaLayerConfigurations.builder()
            .addDirectoryContents(LayerType.CLASSES, sourceRoot, path -> true, basePath)
            .build();
    Assert.assertEquals(
        Arrays.asList(new LayerEntry(sourceRoot.resolve("leaf"), basePath.resolve("leaf"))),
        configurations.getClassLayerEntries());
  }

  @Test
  public void testAddDirectoryContents_directoriesAdded() throws IOException {
    temporaryFolder.newFolder("non-empty", "leaf");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/path/in/container");

    JavaLayerConfigurations configurations =
        JavaLayerConfigurations.builder()
            .addDirectoryContents(LayerType.RESOURCES, sourceRoot, path -> true, basePath)
            .build();
    Assert.assertEquals(
        Arrays.asList(
            new LayerEntry(sourceRoot.resolve("non-empty"), basePath.resolve("non-empty")),
            new LayerEntry(
                sourceRoot.resolve("non-empty/leaf"), basePath.resolve("non-empty/leaf"))),
        configurations.getResourceLayerEntries());
  }

  @Test
  public void testAddDirectoryContents_filter() throws IOException {
    temporaryFolder.newFile("non-target");
    temporaryFolder.newFolder("sub");
    temporaryFolder.newFile("sub/target");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/");

    Predicate<Path> nameIsTarget = path -> "target".equals(path.getFileName().toString());
    JavaLayerConfigurations configurations =
        JavaLayerConfigurations.builder()
            .addDirectoryContents(LayerType.DEPENDENCIES, sourceRoot, nameIsTarget, basePath)
            .build();
    Assert.assertEquals(
        Arrays.asList(
            new LayerEntry(sourceRoot.resolve("sub"), basePath.resolve("sub")),
            new LayerEntry(sourceRoot.resolve("sub/target"), basePath.resolve("sub/target"))),
        configurations.getDependencyLayerEntries());
  }

  @Test
  public void testAddDirectoryContents_directoriesForced() throws IOException {
    temporaryFolder.newFolder("sub", "leaf");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/path/in/container");

    JavaLayerConfigurations configurations =
        JavaLayerConfigurations.builder()
            .addDirectoryContents(LayerType.EXTRA_FILES, sourceRoot, path -> false, basePath)
            .build();
    Assert.assertEquals(
        Arrays.asList(
            new LayerEntry(sourceRoot.resolve("sub"), basePath.resolve("sub")),
            new LayerEntry(sourceRoot.resolve("sub/leaf"), basePath.resolve("sub/leaf"))),
        configurations.getExtraFilesLayerEntries());
  }

  @Test
  public void testAddDirectoryContents_fileAsSourceRoot() throws IOException {
    Path sourceFile = temporaryFolder.newFile("foo").toPath();

    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/");
    JavaLayerConfigurations.Builder builder = JavaLayerConfigurations.builder();
    try {
      builder.addDirectoryContents(LayerType.DEPENDENCIES, sourceFile, path -> true, basePath);
      Assert.fail();
    } catch (NotDirectoryException ex) {
      Assert.assertThat(ex.getMessage(), CoreMatchers.containsString("foo is not a directory"));
    }
  }

  @Test
  public void testAddDirectoryContents_complex() throws IOException {
    temporaryFolder.newFile("A.class");
    temporaryFolder.newFile("B.java");
    temporaryFolder.newFolder("example", "dir");
    temporaryFolder.newFile("example/dir/C.class");
    temporaryFolder.newFile("example/C.class");
    temporaryFolder.newFolder("test", "resources", "leaf");
    temporaryFolder.newFile("test/resources/D.java");
    temporaryFolder.newFile("test/D.class");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/base");

    Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");

    JavaLayerConfigurations configurations =
        JavaLayerConfigurations.builder()
            .addDirectoryContents(LayerType.EXTRA_FILES, sourceRoot, isClassFile, basePath)
            .build();

    assertSourcePathsUnordered(
        Arrays.asList(
            sourceRoot.resolve("A.class"),
            sourceRoot.resolve("example"),
            sourceRoot.resolve("example/dir"),
            sourceRoot.resolve("example/dir/C.class"),
            sourceRoot.resolve("example/C.class"),
            sourceRoot.resolve("test"),
            sourceRoot.resolve("test/resources"),
            sourceRoot.resolve("test/resources/leaf"),
            sourceRoot.resolve("test/D.class")),
        configurations.getExtraFilesLayerEntries());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/base/A.class",
            "/base/example",
            "/base/example/dir",
            "/base/example/dir/C.class",
            "/base/example/C.class",
            "/base/test",
            "/base/test/resources",
            "/base/test/resources/leaf",
            "/base/test/D.class"),
        configurations.getExtraFilesLayerEntries());
  }
}
