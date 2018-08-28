/*
 * Copyright 2018 Google LLC. All rights reserved.
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
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link DefaultCacheReadEntry}. */
@RunWith(MockitoJUnitRunner.class)
public class DefaultCacheReadEntryTest {

  @Mock private DescriptorDigest mockLayerDigest;
  @Mock private DescriptorDigest mockLayerDiffId;

  @Test
  public void testBuilder_fail() {
    try {
      DefaultCacheReadEntry.builder().build();
      Assert.fail("missing required");

    } catch (NullPointerException ex) {
      Assert.assertThat(ex.getMessage(), CoreMatchers.containsString("layerDigest"));
    }

    try {
      DefaultCacheReadEntry.builder().setLayerDigest(mockLayerDigest).build();
      Assert.fail("missing required");

    } catch (NullPointerException ex) {
      Assert.assertThat(ex.getMessage(), CoreMatchers.containsString("layerDiffId"));
    }

    try {
      DefaultCacheReadEntry.builder()
          .setLayerDigest(mockLayerDigest)
          .setLayerDiffId(mockLayerDiffId)
          .build();
      Assert.fail("missing required");

    } catch (NullPointerException ex) {
      Assert.assertThat(ex.getMessage(), CoreMatchers.containsString("layerBlob"));
    }
  }

  @Test
  public void testBuilder_noMetadataBlob() throws IOException {
    CacheReadEntry cacheReadEntry =
        DefaultCacheReadEntry.builder()
            .setLayerDigest(mockLayerDigest)
            .setLayerDiffId(mockLayerDiffId)
            .setLayerSize(1337)
            .setLayerBlob(Blobs.from("layerBlob"))
            .build();
    Assert.assertEquals(mockLayerDigest, cacheReadEntry.getLayerDigest());
    Assert.assertEquals(mockLayerDiffId, cacheReadEntry.getLayerDiffId());
    Assert.assertEquals(1337, cacheReadEntry.getLayerSize());
    Assert.assertEquals("layerBlob", Blobs.writeToString(cacheReadEntry.getLayerBlob()));
    Assert.assertFalse(cacheReadEntry.getMetadataBlob().isPresent());
  }

  @Test
  public void testBuilder_withMetadataBlob() throws IOException {
    CacheReadEntry cacheReadEntry =
        DefaultCacheReadEntry.builder()
            .setLayerDigest(mockLayerDigest)
            .setLayerDiffId(mockLayerDiffId)
            .setLayerSize(1337)
            .setLayerBlob(Blobs.from("layerBlob"))
            .setMetadataBlob(Blobs.from("metadataBlob"))
            .build();
    Assert.assertEquals(mockLayerDigest, cacheReadEntry.getLayerDigest());
    Assert.assertEquals(mockLayerDiffId, cacheReadEntry.getLayerDiffId());
    Assert.assertEquals(1337, cacheReadEntry.getLayerSize());
    Assert.assertEquals("layerBlob", Blobs.writeToString(cacheReadEntry.getLayerBlob()));
    Assert.assertEquals(
        "metadataBlob", Blobs.writeToString(cacheReadEntry.getMetadataBlob().orElse(null)));
  }
}
