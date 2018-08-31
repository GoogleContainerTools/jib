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

/** Tests for {@link DefaultCacheWrite}. */
@RunWith(MockitoJUnitRunner.class)
public class DefaultCacheWriteTest {

  @Mock private DescriptorDigest mockSelector;

  @Test
  public void testLayerOnly() throws IOException {
    CacheWrite cacheWrite = DefaultCacheWrite.layerOnly(Blobs.from("layerBlob"));
    Assert.assertEquals("layerBlob", Blobs.writeToString(cacheWrite.getLayerBlob()));
    Assert.assertFalse(cacheWrite.getSelector().isPresent());
    Assert.assertFalse(cacheWrite.getMetadataBlob().isPresent());
  }

  @Test
  public void testWithSelectorAndMetadata() throws IOException {
    CacheWrite cacheWrite =
        DefaultCacheWrite.withSelectorAndMetadata(
            Blobs.from("layerBlob"), mockSelector, Blobs.from("metadataBlob"));
    Assert.assertEquals("layerBlob", Blobs.writeToString(cacheWrite.getLayerBlob()));
    Assert.assertEquals(mockSelector, cacheWrite.getSelector().orElse(null));
    Assert.assertEquals(
        "metadataBlob", Blobs.writeToString(cacheWrite.getMetadataBlob().orElse(null)));
  }
}
