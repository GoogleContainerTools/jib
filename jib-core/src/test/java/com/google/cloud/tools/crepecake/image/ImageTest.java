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

package com.google.cloud.tools.crepecake.image;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link Image}. */
@RunWith(MockitoJUnitRunner.class)
public class ImageTest {

  @Mock private Layer mockLayer;
  @Mock private ImageLayers<Layer> mockImageLayers;

  @InjectMocks private Image image;

  @Test
  public void test_smokeTest() throws DuplicateLayerException, LayerPropertyNotFoundException {
    ImmutableList<String> expectedEnvironment =
        ImmutableList.of("crepecake=is great", "VARIABLE=VALUE");

    image.setEnvironmentVariable("crepecake", "is great");
    image.setEnvironmentVariable("VARIABLE", "VALUE");

    image.setEntrypoint(Arrays.asList("some", "command"));

    image.addLayer(mockLayer);

    Mockito.verify(mockImageLayers).add(mockLayer);

    Assert.assertEquals(expectedEnvironment, image.getEnvironment());

    Assert.assertEquals(Arrays.asList("some", "command"), image.getEntrypoint());
  }
}
