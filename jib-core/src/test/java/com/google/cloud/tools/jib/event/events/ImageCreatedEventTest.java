/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.jib.event.events;

import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.Image;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/** Tests for {@link ImageCreatedEvent}. */
public class ImageCreatedEventTest {

  @Test
  public void testCreation() {
    Image<?> mockImage = Mockito.mock(Image.class);
    DescriptorDigest mockImageDigest = Mockito.mock(DescriptorDigest.class, "imageDigest");
    DescriptorDigest mockImageId = Mockito.mock(DescriptorDigest.class, "imageId");
    ImageCreatedEvent event = new ImageCreatedEvent(mockImage, mockImageDigest, mockImageId);

    Assert.assertEquals(mockImage, event.getImage());
    Assert.assertEquals(mockImageDigest, event.getImageDigest());
    Assert.assertEquals(mockImageId, event.getImageId());
  }
}
