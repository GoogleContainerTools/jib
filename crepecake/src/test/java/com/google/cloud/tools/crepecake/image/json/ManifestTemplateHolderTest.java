/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.image.json;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link ManifestTemplateHolder}. */
public class ManifestTemplateHolderTest {

  @Test
  public void testFromJson_v21()
      throws IOException, URISyntaxException, UnknownManifestFormatException {
    // Loads the JSON string.
    File v21ManifestJsonFile =
        new File(getClass().getClassLoader().getResource("json/v21manifest.json").toURI());
    String v21ManifestJson =
        CharStreams.toString(
            new InputStreamReader(new FileInputStream(v21ManifestJsonFile), Charsets.UTF_8));

    ManifestTemplateHolder manifestTemplateHolder =
        ManifestTemplateHolder.fromJson(v21ManifestJson);

    Assert.assertTrue(manifestTemplateHolder.isV21());
    Assert.assertFalse(manifestTemplateHolder.isV22());
    Assert.assertNotNull(manifestTemplateHolder.getV21ManifestTemplate());
  }

  @Test
  public void testFromJson_v22()
      throws IOException, URISyntaxException, UnknownManifestFormatException {
    // Loads the JSON string.
    File v22ManifestJsonFile =
        new File(getClass().getClassLoader().getResource("json/v22manifest.json").toURI());
    String v21ManifestJson =
        CharStreams.toString(
            new InputStreamReader(new FileInputStream(v22ManifestJsonFile), Charsets.UTF_8));

    ManifestTemplateHolder manifestTemplateHolder =
        ManifestTemplateHolder.fromJson(v21ManifestJson);

    Assert.assertFalse(manifestTemplateHolder.isV21());
    Assert.assertTrue(manifestTemplateHolder.isV22());
    Assert.assertNotNull(manifestTemplateHolder.getV22ManifestTemplate());
  }
}
