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
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReproducibleJarConverterTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void smokeTest() throws IOException {

    Path destinationJar = temporaryFolder.getRoot().toPath().resolve("test.jar");

    OutputStream fileOutputStream = Files.newOutputStream(destinationJar);
    ReproducibleJarConverter jarConverter = new ReproducibleJarConverter(Paths.get("src/test/resources/core/jar/complexLib.jar"));
    jarConverter.convert(fileOutputStream);

    JarFile evaluatedJar = new JarFile(destinationJar.toFile());

    ZipEntry zipEntry = evaluatedJar.getEntry("complex/Complex.class");
    //Assert.assertEquals(FileTime.from(LayerConfiguration.DEFAULT_MODIFICATION_TIME), zipEntry.getCreationTime());
    Assert.assertEquals(FileTime.from(LayerConfiguration.DEFAULT_MODIFICATION_TIME), zipEntry.getLastModifiedTime());
    Assert.assertEquals(FileTime.from(LayerConfiguration.DEFAULT_MODIFICATION_TIME), zipEntry.getLastAccessTime());

    Manifest manifest = evaluatedJar.getManifest();
    for (String attr : ReproducibleJarConverter.MANIFEST_ATTRIBUTES_TO_STRIP) {
      Assert.assertFalse(manifest.getEntries().containsKey(attr));
      Assert.assertFalse(manifest.getMainAttributes().containsKey(new Attributes.Name(attr)));
    }
  }

}