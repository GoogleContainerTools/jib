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
import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.FilePermissions;
import com.google.cloud.tools.jib.api.LayerConfiguration;
import com.google.cloud.tools.jib.api.LayerEntry;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link SkaffoldFilesOutput}. */
public class SkaffoldSyncMapTemplateTest {

  private static final String TEST_JSON =
      "{\"generated\":[{\"src\":\"/make\",\"dest\":\"/it\"}],\"direct\":[{\"src\":\"/makeit\",\"dest\":\"/so\"},{\"src\":\"/ener\",\"dest\":\"/gize\"}]}";
  private static final String TEST_JSON_EMPTY_GENERATED =
      "{\"generated\":[],\"direct\":[{\"src\":\"/makeit\",\"dest\":\"/so\"},{\"src\":\"/ener\",\"dest\":\"/gize\"}]}";
  private static final String TEST_JSON_NO_GENERATED =
      "{\"direct\":[{\"src\":\"/makeit\",\"dest\":\"/so\"},{\"src\":\"/ener\",\"dest\":\"/gize\"}]}";
  private static final String FAIL_TEST_JSON_MISSING_FIELD =
      "{\"generated\":[{\"src\":\"/makeit\"}],\"direct\":[{\"src\":\"/makeit\",\"dest\":\"/so\"},{\"src\":\"/ener\",\"dest\":\"/gize\"}]}";
  private static final String FAIL_TEST_JSON_BAD_PROPERTY_NAME =
      "{\"generated\":[{\"picard\":\"/makeit\",\"src\":\"/make\",\"dest\":\"/it\"}],\"direct\":[{\"src\":\"/makeit\",\"dest\":\"so\"},{\"src\":\"/ener\",\"dest\":\"/gize\"}]}";

  @Test
  public void testFrom_badPropertyName() throws IOException {
    try {
      SkaffoldSyncMapTemplate.from(FAIL_TEST_JSON_BAD_PROPERTY_NAME);
      Assert.fail();
    } catch (UnrecognizedPropertyException ex) {
      Assert.assertTrue(ex.getMessage().contains("Unrecognized field \"picard\""));
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

  @Test
  public void testFrom_validEmpty() throws Exception {
    SkaffoldSyncMapTemplate.from(TEST_JSON_EMPTY_GENERATED);
    SkaffoldSyncMapTemplate.from(TEST_JSON_NO_GENERATED);
    // pass if no exceptions
  }

  @Test
  public void testGetJsonString() throws IOException {
    SkaffoldSyncMapTemplate ssmt = new SkaffoldSyncMapTemplate();
    ssmt.addGenerated(
        new LayerEntry(
            Paths.get("/make"),
            AbsoluteUnixPath.get("/it"),
            FilePermissions.DEFAULT_FILE_PERMISSIONS,
            LayerConfiguration.DEFAULT_MODIFICATION_TIME));
    ssmt.addDirect(
        new LayerEntry(
            Paths.get("/makeit"),
            AbsoluteUnixPath.get("/so"),
            FilePermissions.DEFAULT_FILE_PERMISSIONS,
            LayerConfiguration.DEFAULT_MODIFICATION_TIME));
    ssmt.addDirect(
        new LayerEntry(
            Paths.get("/ener"),
            AbsoluteUnixPath.get("/gize"),
            FilePermissions.DEFAULT_FILE_PERMISSIONS,
            LayerConfiguration.DEFAULT_MODIFICATION_TIME));
    Assert.assertEquals(TEST_JSON, ssmt.getJsonString());
  }

  @Test
  public void testGetJsonString_emptyGenerated() throws IOException {
    SkaffoldSyncMapTemplate ssmt = new SkaffoldSyncMapTemplate();
    ssmt.addDirect(
        new LayerEntry(
            Paths.get("/makeit"),
            AbsoluteUnixPath.get("/so"),
            FilePermissions.DEFAULT_FILE_PERMISSIONS,
            LayerConfiguration.DEFAULT_MODIFICATION_TIME));
    ssmt.addDirect(
        new LayerEntry(
            Paths.get("/ener"),
            AbsoluteUnixPath.get("/gize"),
            FilePermissions.DEFAULT_FILE_PERMISSIONS,
            LayerConfiguration.DEFAULT_MODIFICATION_TIME));
    Assert.assertEquals(TEST_JSON_EMPTY_GENERATED, ssmt.getJsonString());
  }
}
