package com.google.cloud.tools.jib.jar;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class ReproducibleJarConverterTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final Path srcJar = Paths.get("src/test/resources/core/jar/complexLib.jar");
  private Path convertedJar;
  private Path extractionRoot;

  private Path complexDir;
  private Path complexFile;

  private Path manifestDir;
  private Path manifestFile;

  @Before
  public void covertJar() throws IOException, InterruptedException {
    convertedJar = temporaryFolder.getRoot().toPath().resolve("test.jar");

    OutputStream fileOutputStream = Files.newOutputStream(convertedJar);
    ReproducibleJarConverter jarConverter = new ReproducibleJarConverter(srcJar);
    jarConverter.convert(fileOutputStream);

    extractionRoot = convertedJar.getParent();
    ProcessBuilder pb = new ProcessBuilder();
    pb.directory(extractionRoot.toFile());
    pb.command("jar", "-xf", convertedJar.toAbsolutePath().toString());
    pb.start().waitFor();

    complexDir = extractionRoot.resolve("complex");
    complexFile = complexDir.resolve("Complex.class");

    manifestDir = extractionRoot.resolve("META-INF");
    manifestFile = manifestDir.resolve("MANIFEST.MF");
  }

  @Test
  public void testManifest() throws IOException {
    JarFile evaluatedJar = new JarFile(convertedJar.toFile());

    Manifest manifest = evaluatedJar.getManifest();
    for (String attr : ReproducibleJarConverter.MANIFEST_ATTRIBUTES_TO_STRIP) {
      Assert.assertFalse(manifest.getEntries().containsKey(attr));
      Assert.assertFalse(manifest.getMainAttributes().containsKey(new Attributes.Name(attr)));
    }
  }

  @Test
  public void testExtractedFiles() throws IOException {
    Assert.assertTrue(Files.isDirectory(complexDir));
    Assert.assertTrue(Files.isRegularFile(complexFile));

    Assert.assertTrue(Files.isDirectory(manifestDir));
    Assert.assertTrue(Files.isRegularFile(manifestFile));

    FileTime expected = FileTime.fromMillis(ReproducibleJarConverter.CONSTANT_TIME_FOR_ZIP_ENTRIES);
    for (Path path : ImmutableList.of(complexFile, complexDir, manifestFile, manifestDir)) {
      BasicFileAttributes basicFileAttributes =
          Files.readAttributes(path, BasicFileAttributes.class);
      Assert.assertEquals(expected, basicFileAttributes.lastModifiedTime());
    }
  }

  @Test
  public void testFiles() throws IOException {

    ZipFile zipFile = new ZipFile(convertedJar.toFile());

    for (ZipEntry entry : Collections.list(zipFile.entries())) {
      Assert.assertEquals(ReproducibleJarConverter.CONSTANT_TIME_FOR_ZIP_ENTRIES, entry.getTime());
    }
  }

  @Test
  public void testManifestEntries() throws IOException {
    Manifest jarManifest = new JarFile(convertedJar.toFile()).getManifest();

    for (String x : ReproducibleJarConverter.MANIFEST_ATTRIBUTES_TO_STRIP) {
      Assert.assertFalse(jarManifest.getEntries().containsKey(x));
      Assert.assertFalse(jarManifest.getMainAttributes().containsKey(x));
    }

    // we didn't erase everything
    Assert.assertTrue(
        jarManifest.getMainAttributes().containsKey(Attributes.Name.MANIFEST_VERSION));
  }

  @Test
  public void testIsSigned() throws IOException {
    Path unsignedJar = srcJar;
    Path signedJar = srcJar.getParent().resolve("complexLibSigned.jar");

    Assert.assertFalse(new ReproducibleJarConverter(unsignedJar).isSigned());
    Assert.assertTrue(new ReproducibleJarConverter(signedJar).isSigned());
  }
}
