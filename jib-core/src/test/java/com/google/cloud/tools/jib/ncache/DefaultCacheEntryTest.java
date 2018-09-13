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
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link DefaultCacheEntry}. */
@RunWith(MockitoJUnitRunner.class)
public class DefaultCacheEntryTest {

  @Mock private DescriptorDigest mockLayerDigest;
  @Mock private DescriptorDigest mockLayerDiffId;

  @Test
  public void testBuilder_fail() {
    try {
      DefaultCacheEntry.builder().build();
      Assert.fail("missing required");

    } catch (NullPointerException ex) {
      Assert.assertThat(ex.getMessage(), CoreMatchers.containsString("layerDigest"));
    }

    try {
      DefaultCacheEntry.builder().setLayerDigest(mockLayerDigest).build();
      Assert.fail("missing required");

    } catch (NullPointerException ex) {
      Assert.assertThat(ex.getMessage(), CoreMatchers.containsString("layerDiffId"));
    }

    try {
      DefaultCacheEntry.builder()
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
    DefaultCacheEntry.Builder cacheEntryBuilder =
        DefaultCacheEntry.builder()
            .setLayerDigest(mockLayerDigest)
            .setLayerDiffId(mockLayerDiffId)
            .setLayerSize(1337);
    Assert.assertFalse(cacheEntryBuilder.hasLayerBlob());
    cacheEntryBuilder.setLayerBlob(Blobs.from("layerBlob"));
    Assert.assertTrue(cacheEntryBuilder.hasLayerBlob());
    CacheEntry cacheEntry = cacheEntryBuilder.build();
    Assert.assertEquals(mockLayerDigest, cacheEntry.getLayerDigest());
    Assert.assertEquals(mockLayerDiffId, cacheEntry.getLayerDiffId());
    Assert.assertEquals(1337, cacheEntry.getLayerSize());
    Assert.assertEquals("layerBlob", Blobs.writeToString(cacheEntry.getLayerBlob()));
    Assert.assertFalse(cacheEntry.getMetadataBlob().isPresent());
  }

  @Test
  public void testBuilder_withMetadataBlob() throws IOException {
    DefaultCacheEntry.Builder cacheEntryBuilder =
        DefaultCacheEntry.builder()
            .setLayerDigest(mockLayerDigest)
            .setLayerDiffId(mockLayerDiffId)
            .setLayerSize(1337)
            .setLayerBlob(Blobs.from("layerBlob"));
    Assert.assertFalse(cacheEntryBuilder.hasMetadataBlob());
    cacheEntryBuilder.setMetadataBlob(Blobs.from("metadataBlob"));
    Assert.assertTrue(cacheEntryBuilder.hasMetadataBlob());
    CacheEntry cacheEntry = cacheEntryBuilder.build();
    Assert.assertEquals(mockLayerDigest, cacheEntry.getLayerDigest());
    Assert.assertEquals(mockLayerDiffId, cacheEntry.getLayerDiffId());
    Assert.assertEquals(1337, cacheEntry.getLayerSize());
    Assert.assertEquals("layerBlob", Blobs.writeToString(cacheEntry.getLayerBlob()));
    Assert.assertEquals(
        "metadataBlob", Blobs.writeToString(cacheEntry.getMetadataBlob().orElse(null)));
  }
}
