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

package com.google.cloud.tools.jib.plugins.common;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

class ImageMetadataOutputTest {

  private static final String TEST_JSON =
      "{\"image\":"
          + "\"gcr.io/project/image:tag\","
          + "\"imageId\":"
          + "\"sha256:61bb3ec31a47cb730eb58a38bbfa813761a51dca69d10e39c24c3d00a7b2c7a9\","
          + "\"imageDigest\":"
          + "\"sha256:3f1be7e19129edb202c071a659a4db35280ab2bb1a16f223bfd5d1948657b6fc\","
          + "\"tags\":[\"latest\",\"tag\"],"
          + "\"imagePushed\":true"
          + "}";

  @Test
  void testFromJson() throws IOException {
    ImageMetadataOutput output = ImageMetadataOutput.fromJson(TEST_JSON);
    Assert.assertEquals("gcr.io/project/image:tag", output.getImage());
    Assert.assertEquals(
        "sha256:61bb3ec31a47cb730eb58a38bbfa813761a51dca69d10e39c24c3d00a7b2c7a9",
        output.getImageId());
    Assert.assertEquals(
        "sha256:3f1be7e19129edb202c071a659a4db35280ab2bb1a16f223bfd5d1948657b6fc",
        output.getImageDigest());
    Assert.assertTrue(output.isImagePushed());

    Assert.assertEquals(ImmutableList.of("latest", "tag"), output.getTags());
  }

  @Test
  void testToJson() throws IOException {
    ImageMetadataOutput output = ImageMetadataOutput.fromJson(TEST_JSON);
    Assert.assertEquals(TEST_JSON, output.toJson());
  }
}
