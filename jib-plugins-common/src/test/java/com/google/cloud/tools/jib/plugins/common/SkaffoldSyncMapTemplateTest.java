/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.plugins.common;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link SkaffoldFilesOutput}. */
public class SkaffoldSyncMapTemplateTest {

  private static final Path GEN_SRC = Paths.get("/gen/src/").toAbsolutePath();
  private static final Path DIR_SRC_1 = Paths.get("/dir/src/").toAbsolutePath();
  private static final Path DIR_SRC_2 = Paths.get("/dir/src/").toAbsolutePath();

  private static final String TEST_JSON =
      "{\"generated\":[{\"src\":\""
          + getPathForJson(GEN_SRC)
          + "\",\"dest\":\"/genDest\"}],\"direct\":[{\"src\":\""
          + getPathForJson(DIR_SRC_1)
          + "\",\"dest\":\"/dirDest1\"},{\"src\":\""
          + getPathForJson(DIR_SRC_2)
          + "\",\"dest\":\"/dirDest2\"}]}";
  private static final String TEST_JSON_EMPTY_GENERATED =
      "{\"generated\":[],\"direct\":[{\"src\":\""
          + getPathForJson(DIR_SRC_1)
          + "\",\"dest\":\"/dirDest1\"},{\"src\":\""
          + getPathForJson(DIR_SRC_2)
          + "\",\"dest\":\"/dirDest2\"}]}";
  private static final String TEST_JSON_NO_GENERATED =
      "{\"direct\":[{\"src\":\""
          + getPathForJson(DIR_SRC_1)
          + "\",\"dest\":\"/dirDest1\"},{\"src\":\""
          + getPathForJson(DIR_SRC_2)
          + "\",\"dest\":\"/dirDest2\"}]}";
  private static final String FAIL_TEST_JSON_MISSING_FIELD =
      "{\"generated\":[{\"src\":\""
          + getPathForJson(GEN_SRC)
          + "\"}],\"direct\":[{\"src\":\""
          + getPathForJson(DIR_SRC_1)
          + "\",\"dest\":\"/dirDest1\"},{\"src\":\""
          + getPathForJson(DIR_SRC_2)
          + "\",\"dest\":\"/dirDest2\"}]}";
  private static final String FAIL_TEST_JSON_BAD_PROPERTY_NAME =
      "{\"generated\":[{\"jean-luc\":\"picard\", \"src\":\""
          + getPathForJson(GEN_SRC)
          + "\",\"dest\":\"/genDest\"}],\"direct\":[{\"src\":\""
          + getPathForJson(DIR_SRC_1)
          + "\",\"dest\":\"/dirDest1\"},{\"src\":\""
          + getPathForJson(DIR_SRC_2)
          + "\",\"dest\":\"/dirDest2\"}]}";

  // manually correct "\" that we inject into the strings above for windows paths, this is only
  // needed for this test, when json writes the string out in the actual code, it does the right
  // thing
  private static String getPathForJson(Path path) {
    return path.toString().replace("\\", "\\\\");
  }

  @Test
  public void testFrom_badPropertyName() throws IOException {
    try {
      SkaffoldSyncMapTemplate.from(FAIL_TEST_JSON_BAD_PROPERTY_NAME);
      Assert.fail();
    } catch (UnrecognizedPropertyException ex) {
      Assert.assertTrue(ex.getMessage().contains("Unrecognized field \"jean-luc\""));
    }
  }

  @Test
  public void testFrom_missingField() throws IOException {
    try {
      SkaffoldSyncMapTemplate.from(FAIL_TEST_JSON_MISSING_FIELD);
      Assert.fail();
    } catch (MismatchedInputException ex) {
      Assert.assertTrue(ex.getMessage().contains("Missing required creator property 'dest'"));
    }
  }

  @Test(expected = Test.None.class /* no exception expected */)
  public void testFrom_validEmpty() throws Exception {
    SkaffoldSyncMapTemplate.from(TEST_JSON_EMPTY_GENERATED);
    SkaffoldSyncMapTemplate.from(TEST_JSON_NO_GENERATED);
    // pass if no exceptions
  }

  @Test
  public void testGetJsonString() throws IOException {
    SkaffoldSyncMapTemplate ssmt = new SkaffoldSyncMapTemplate();
    ssmt.addGenerated(
        new FileEntry(
            GEN_SRC,
            AbsoluteUnixPath.get("/genDest"),
            FilePermissions.DEFAULT_FILE_PERMISSIONS,
            FileEntriesLayer.DEFAULT_MODIFICATION_TIME));
    ssmt.addDirect(
        new FileEntry(
            DIR_SRC_1,
            AbsoluteUnixPath.get("/dirDest1"),
            FilePermissions.DEFAULT_FILE_PERMISSIONS,
            FileEntriesLayer.DEFAULT_MODIFICATION_TIME));
    ssmt.addDirect(
        new FileEntry(
            DIR_SRC_2,
            AbsoluteUnixPath.get("/dirDest2"),
            FilePermissions.DEFAULT_FILE_PERMISSIONS,
            FileEntriesLayer.DEFAULT_MODIFICATION_TIME));
    Assert.assertEquals(TEST_JSON, ssmt.getJsonString());
  }

  @Test
  public void testGetJsonString_emptyGenerated() throws IOException {
    SkaffoldSyncMapTemplate ssmt = new SkaffoldSyncMapTemplate();
    ssmt.addDirect(
        new FileEntry(
            DIR_SRC_1,
            AbsoluteUnixPath.get("/dirDest1"),
            FilePermissions.DEFAULT_FILE_PERMISSIONS,
            FileEntriesLayer.DEFAULT_MODIFICATION_TIME));
    ssmt.addDirect(
        new FileEntry(
            DIR_SRC_2,
            AbsoluteUnixPath.get("/dirDest2"),
            FilePermissions.DEFAULT_FILE_PERMISSIONS,
            FileEntriesLayer.DEFAULT_MODIFICATION_TIME));
    Assert.assertEquals(TEST_JSON_EMPTY_GENERATED, ssmt.getJsonString());
  }
}
