/*
 * Copyright 2017 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.image;

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.configuration.Port;
import com.google.cloud.tools.jib.configuration.Port.Protocol;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link Image}. */
@RunWith(MockitoJUnitRunner.class)
public class ImageTest {

  @Mock private Layer mockLayer;
  @Mock private DescriptorDigest mockDescriptorDigest;

  @Before
  public void setUp() throws LayerPropertyNotFoundException {
    Mockito.when(mockLayer.getBlobDescriptor())
        .thenReturn(new BlobDescriptor(mockDescriptorDigest));
  }

  @Test
  public void test_smokeTest() throws LayerPropertyNotFoundException {
    ImmutableList<String> expectedEnvironment =
        ImmutableList.of("crepecake=is great", "VARIABLE=VALUE");

    Image<Layer> image =
        Image.builder()
            .setEnvironmentVariable("crepecake", "is great")
            .setEnvironmentVariable("VARIABLE", "VALUE")
            .setEntrypoint(Arrays.asList("some", "command"))
            .setJavaArguments(Arrays.asList("arg1", "arg2"))
            .setExposedPorts(
                ImmutableList.of(new Port(1000, Protocol.TCP), new Port(2000, Protocol.TCP)))
            .addLayer(mockLayer)
            .build();

    Assert.assertEquals(
        mockDescriptorDigest, image.getLayers().get(0).getBlobDescriptor().getDigest());
    Assert.assertEquals(expectedEnvironment, image.getEnvironment());
    Assert.assertEquals(Arrays.asList("some", "command"), image.getEntrypoint());
    Assert.assertEquals(Arrays.asList("arg1", "arg2"), image.getJavaArguments());
    Assert.assertEquals(
        ImmutableList.of(new Port(1000, Protocol.TCP), new Port(2000, Protocol.TCP)),
        image.getExposedPorts());
  }
}
