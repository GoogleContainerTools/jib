package com.google.cloud.tools.jib.jar;

import com.google.cloud.tools.jib.api.LayerConfiguration;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class ReproducibleJarConverterTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final Path srcJar = Paths.get("src/test/resources/core/jar/complexLib.jar");
  private Path convertedJar;
  private Path extractionRoot;

  @Before
  public void covertJar() throws IOException {
    convertedJar = temporaryFolder.getRoot().toPath().resolve("test.jar");

    OutputStream fileOutputStream = Files.newOutputStream(convertedJar);
    ReproducibleJarConverter jarConverter = new ReproducibleJarConverter(Paths.get("src/test/resources/core/jar/complexLib.jar"));
    jarConverter.convert(fileOutputStream);

    Path extractionRoot = convertedJar.getParent();
    ProcessBuilder pb = new ProcessBuilder();
    pb.directory(extractionRoot.toFile());
    pb.command("jar", "-xf", convertedJar.toAbsolutePath().toString());
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
  public void testFiles() {
    Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"));

    Assert.assertTrue(Files.isDirectory(extractionRoot.resolve("complex")));
  }

  @Test
  public void testEntries() throws IOException {

    JarFile evaluatedJar = new JarFile(convertedJar.toFile());

    ZipEntry zipEntry = evaluatedJar.getEntry("complex/Complex.class");
    Assert.assertEquals(FileTime.from(LayerConfiguration.DEFAULT_MODIFICATION_TIME), zipEntry.getCreationTime());
    Assert.assertEquals(FileTime.from(LayerConfiguration.DEFAULT_MODIFICATION_TIME), zipEntry.getLastModifiedTime());
    Assert.assertEquals(FileTime.from(LayerConfiguration.DEFAULT_MODIFICATION_TIME), zipEntry.getLastAccessTime());
  }
}