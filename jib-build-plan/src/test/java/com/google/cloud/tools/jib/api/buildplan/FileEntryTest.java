package com.google.cloud.tools.jib.api.buildplan;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Paths;
import java.time.Instant;

public class FileEntryTest {

  @Test
  public void testToString() {
     Assert.assertEquals("{a/path,/an/absolute/unix/path,333,1970-01-01T00:00:00Z,0:0}", new FileEntry(Paths.get("a/path"), AbsoluteUnixPath.get("/an/absolute/unix/path"), FilePermissions.fromOctalString("333"), Instant.EPOCH, "0:0").toString());
  }
}
