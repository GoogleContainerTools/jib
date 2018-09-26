/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.ncache;

import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link UncompressedCacheWrite}. */
@RunWith(MockitoJUnitRunner.class)
public class UncompressedCacheWriteTest {

  @Mock private DescriptorDigest mockSelector;

  @Test
  public void testLayerOnly() throws IOException {
    UncompressedCacheWrite uncompressedCacheWrite = UncompressedCacheWrite.layerOnly(Blobs.from("layerBlob"));
    Assert.assertEquals("layerBlob", Blobs.writeToString(uncompressedCacheWrite.getUncompressedLayerBlob()));
    Assert.assertFalse(uncompressedCacheWrite.getSelector().isPresent());
    Assert.assertFalse(uncompressedCacheWrite.getMetadataBlob().isPresent());
  }

  @Test
  public void testWithSelectorAndMetadata() throws IOException {
    UncompressedCacheWrite uncompressedCacheWrite =
        UncompressedCacheWrite.withSelectorAndMetadata(
            Blobs.from("layerBlob"), mockSelector, Blobs.from("metadataBlob"));
    Assert.assertEquals("layerBlob", Blobs.writeToString(uncompressedCacheWrite.getUncompressedLayerBlob()));
    Assert.assertEquals(mockSelector, uncompressedCacheWrite.getSelector().orElse(null));
    Assert.assertEquals(
        "metadataBlob", Blobs.writeToString(uncompressedCacheWrite.getMetadataBlob().orElse(null)));
  }
}
