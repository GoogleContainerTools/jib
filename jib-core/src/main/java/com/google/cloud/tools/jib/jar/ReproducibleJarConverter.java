package com.google.cloud.tools.jib.jar;

import com.google.cloud.tools.jib.api.FilePermissions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Take a jar and create a reproducible version of it. Much of this logic is derived from
 * https://github.com/Zlika/reproducible-build-maven-plugin and
 * https://github.com/gradle/gradle (ZipCopyAction.java)
 */
public class ReproducibleJarConverter {

  // these are all manifest entries we want to remove, I don't know exactly if we cover everything
  // here given a user can add whatever they want :\, but maybe anything that starts with Build*
  // we should remove?
  // maven defaults: https://maven.apache.org/shared/maven-archiver/examples/manifest.html
  // gradle defaults: Manifest-Version only
  @VisibleForTesting
  static final ImmutableList<String> MANIFEST_ATTRIBUTES_TO_STRIP = ImmutableList
      .of("Bnd-LastModified",
          "Build-Jdk",
          "Build-Date",
          "Build-Revision",
          "Build-Time",
          "Build-Timestamp",
          "Build-OS",
          "Built-By",
          "Created-By",
          "OpenIDE-Module-Build-Version");


  // from gradle: ZipCopyAction.java
  public static final long CONSTANT_TIME_FOR_ZIP_ENTRIES = new GregorianCalendar(1980, Calendar.FEBRUARY, 1, 0, 0, 0).getTimeInMillis();

  private final Path jar;

  public ReproducibleJarConverter(Path jar) {
    this.jar = jar;
  }

  public void convert(OutputStream target) throws IOException {
    ZipArchiveOutputStream out = new ZipArchiveOutputStream(target);
    ZipFile zip = new ZipFile(jar.toFile());

    List<ZipArchiveEntry> entries = Collections.list(zip.getEntries());
    entries.sort(Comparator.comparing(ZipArchiveEntry::getName));

    for (ZipArchiveEntry srcEntry : entries) {
      ZipArchiveEntry entry = new ZipArchiveEntry(srcEntry);
      standardizeAttributes(entry);

      if (entry.getName().equals("META-INF/MANIFEST.MF")) {
        handleManifest(entry, srcEntry, zip, out);
      }
      else {
        // if we're not making modifications to the contents, then write the entry directly
        out.addRawArchiveEntry(entry, zip.getRawInputStream(srcEntry));
      }
    }
    out.close();
  }

  // mutates entry and writes to output stream
  private void handleManifest(ZipArchiveEntry entry, ZipArchiveEntry srcEntry, ZipFile zip, ZipArchiveOutputStream out)
      throws IOException {

    Manifest manifest = new Manifest(zip.getInputStream(srcEntry));

    // process manifest -- strip the fields
    MANIFEST_ATTRIBUTES_TO_STRIP.forEach(key -> {
        manifest.getMainAttributes().remove(new Attributes.Name(key));
        manifest.getEntries().remove(key);
      }
    );

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

  private void standardizeAttributes(ZipArchiveEntry entry) {
    // https://unix.stackexchange.com/questions/14705/the-zip-formats-external-file-attribute
    // TTTTsstrwxrwxrwx0000000000ADVSHR
    // ^^^^____________________________ file type as explained (1000 dir, 0100 file)
    //     ^^^_________________________ setuid, setgid, sticky (000)
    //        ^^^^^^^^^________________ permissions (use our defaults)
    //                 ^^^^^^^^________ don't care: unclear (not settable by setUnixMode)
    //                         ^^^^^^^^ don't care: DOS attribute bits (not settable by setUnixMode)
    if (entry.isDirectory()) {
      entry.setUnixMode((0b0100 << 12) + FilePermissions.DEFAULT_FOLDER_PERMISSIONS.getPermissionBits());
    }
    else {
      entry.setUnixMode((0b1000 << 12) + FilePermissions.DEFAULT_FILE_PERMISSIONS.getPermissionBits());
    }

    entry.setTime(CONSTANT_TIME_FOR_ZIP_ENTRIES);

    // clear out extra fields
    entry.setExtraFields(new ZipExtraField[]{});
  }
}
