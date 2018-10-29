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

package com.google.cloud.tools.jib.cache;

import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.io.IOException;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link DefaultCachedLayer}. */
@RunWith(MockitoJUnitRunner.class)
public class DefaultCachedLayerTest {

  @Mock private DescriptorDigest mockLayerDigest;
  @Mock private DescriptorDigest mockLayerDiffId;

  @Test
  public void testBuilder_fail() {
    try {
      DefaultCachedLayer.builder().build();
      Assert.fail("missing required");

    } catch (NullPointerException ex) {
      Assert.assertThat(ex.getMessage(), CoreMatchers.containsString("layerDigest"));
    }

    try {
      DefaultCachedLayer.builder().setLayerDigest(mockLayerDigest).build();
      Assert.fail("missing required");

    } catch (NullPointerException ex) {
      Assert.assertThat(ex.getMessage(), CoreMatchers.containsString("layerDiffId"));
    }

    try {
      DefaultCachedLayer.builder()
          .setLayerDigest(mockLayerDigest)
          .setLayerDiffId(mockLayerDiffId)
          .build();
      Assert.fail("missing required");

    } catch (NullPointerException ex) {
      Assert.assertThat(ex.getMessage(), CoreMatchers.containsString("layerBlob"));
    }
  }

  @Test
  public void testBuilder_pass() throws IOException {
    DefaultCachedLayer.Builder cachedLayerBuilder =
        DefaultCachedLayer.builder()
            .setLayerDigest(mockLayerDigest)
            .setLayerDiffId(mockLayerDiffId)
            .setLayerSize(1337);
    Assert.assertFalse(cachedLayerBuilder.hasLayerBlob());
    cachedLayerBuilder.setLayerBlob(Blobs.from("layerBlob"));
    Assert.assertTrue(cachedLayerBuilder.hasLayerBlob());
    CachedLayer cachedLayer = cachedLayerBuilder.build();
    Assert.assertEquals(mockLayerDigest, cachedLayer.getDigest());
    Assert.assertEquals(mockLayerDiffId, cachedLayer.getDiffId());
    Assert.assertEquals(1337, cachedLayer.getSize());
    Assert.assertEquals("layerBlob", Blobs.writeToString(cachedLayer.getBlob()));
  }
}
