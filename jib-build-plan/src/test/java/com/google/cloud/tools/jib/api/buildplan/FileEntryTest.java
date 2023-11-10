/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.api.buildplan;

import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;
import org.junit.Assert;
import org.junit.Test;

/** File entry tests. */
public class FileEntryTest {

  @Test
  public void testToString() {
    Assert.assertEquals(
        "{a" + File.separator + "path,/an/absolute/unix/path,333,1970-01-01T00:00:00Z,0:0}",
        new FileEntry(
                Paths.get("a/path"),
                AbsoluteUnixPath.get("/an/absolute/unix/path"),
                FilePermissions.fromOctalString("333"),
                Instant.EPOCH,
                "0:0")
            .toString());
  }
}
