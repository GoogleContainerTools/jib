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

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Map;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link Image}. */
public class ImageTest {

  private Digest fakeDigest;
  private Layer fakeLayer;

  @Before
  public void setUpFakes() throws DigestException {
    fakeDigest =
        Digest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad");
    fakeLayer = new Layer(fakeDigest, 1000, fakeDigest);
  }

  @Test
  public void test_smokeTest() throws ImageException {
    Map<String, String> expectedEnvironment =
        ImmutableMap.of("crepecake", "is great", "VARIABLE", "VALUE");

    Image image = new Image();

    image.setEnvironmentVariable("crepecake", "is great");
    image.setEnvironmentVariable("VARIABLE", "VALUE");

    image.setEntrypoint(Arrays.asList("some", "command"));

    image.addLayer(fakeLayer);

    Assert.assertThat(image.getEnvironmentMap(), CoreMatchers.is(expectedEnvironment));

    Assert.assertThat(image.getEntrypoint(), CoreMatchers.is(Arrays.asList("some", "command")));
  }
}
