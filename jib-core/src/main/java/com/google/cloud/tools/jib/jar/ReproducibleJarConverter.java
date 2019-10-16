package com.google.cloud.tools.jib.jar;

import com.google.cloud.tools.jib.api.LayerConfiguration;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.jar.Manifest;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;

/**
 * Take a jar and create a reproducible version of it. Much of this logic is derived from
 * see: https://github.com/Zlika/reproducible-build-maven-plugin
 */
public class ReproducibleJarConverter {

  // these are all manifest entries (not main attributes)
  private static final ImmutableList<String> MANIFEST_ATTRIBUTES_TO_STRIP = ImmutableList.of("Bnd-LastModified", "Build-Jdk", "Build-Date", "Build-Time", "Built-By", "Created-By", "OpenIDE-Module-Build-Version");

  private final Path jar;
  private Instant timestamp = LayerConfiguration.DEFAULT_MODIFICATION_TIME;

  public ReproducibleJarConverter(Path jar) {
    this.jar = jar;
  }

  public ReproducibleJarConverter timestamp(Instant timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public void convert(Path jar, OutputStream target) throws IOException {
    ZipArchiveOutputStream out = new ZipArchiveOutputStream(target);
    ZipFile zip = new ZipFile(jar.toFile());
    List<ZipArchiveEntry> entries = Collections.list(zip.getEntries());
    entries.sort(Comparator.comparing(ZipArchiveEntry::getName));

    for (ZipArchiveEntry srcEntry : entries) {
      ZipArchiveEntry entry = new ZipArchiveEntry(srcEntry);
      standardizeAttributes(entry);

      if (entry.getName().equals("META-INF/MANIFEST.MF")) {
        handleManifest(entry, zip, out);
      }
      else {
        // if we're not making modifications to the contents, then write the entry directly
        out.addRawArchiveEntry(entry, zip.getRawInputStream(srcEntry));
      }
    }
  }

  // mutates entry and writes to output stream
  private void handleManifest(ZipArchiveEntry entry, ZipFile zip, ZipArchiveOutputStream out)
      throws IOException {

    Manifest manifest = new Manifest(zip.getInputStream(entry));

    // process manifest -- strip the fields
    MANIFEST_ATTRIBUTES_TO_STRIP.forEach(key -> manifest.getEntries().remove(key));

    // write out the new manifest to a bytearray
    ByteArrayOutputStream manifestStream = new ByteArrayOutputStream();
    manifest.write(manifestStream);
    byte[] manifestContent = manifestStream.toByteArray();

    entry.setSize(manifestContent.length);

    // write out the entry
    out.putArchiveEntry(entry);
    out.write(manifestContent);
    out.closeArchiveEntry();
  }

  // https://unix.stackexchange.com/questions/14705/the-zip-formats-external-file-attribute
  // TTTTsstrwxrwxrwx0000000000ADVSHR
  // ^^^^____________________________ file type as explained (1000 dir, 0100 file)
  //     ^^^_________________________ setuid, setgid, sticky (000)
  //        ^^^^^^^^^________________ permissions (use our defaults)
  //                 ^^^^^^^^________ unclear (not settable by setUnixMode)
  //                         ^^^^^^^^ DOS attribute bits (not settable by setUnixMode)
  private void standardizeAttributes(ZipArchiveEntry entry) {
    entry.setLastModifiedTime(FileTime.from(timestamp));
    entry.setLastAccessTime(FileTime.from(timestamp));
    if (entry.isDirectory()) {
      entry.setUnixMode((0b0100 << 12) + 0755);
    }
    else {
      entry.setUnixMode((0b1000 << 12) + 0644);
    }
  }
}
