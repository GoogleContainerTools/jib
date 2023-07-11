/*
 * Copyright 2017 Google LLC.
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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link Image}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageTest {

  @Mock private Layer mockLayer;
  @Mock private DescriptorDigest mockDescriptorDigest;

  @BeforeEach
  void setUp() throws LayerPropertyNotFoundException {
    Mockito.when(mockLayer.getBlobDescriptor())
        .thenReturn(new BlobDescriptor(mockDescriptorDigest));
  }

  @Test
  void test_smokeTest() throws LayerPropertyNotFoundException {
    Image image =
        Image.builder(V22ManifestTemplate.class)
            .setCreated(Instant.ofEpochSecond(10000))
            .addEnvironmentVariable("crepecake", "is great")
            .addEnvironmentVariable("VARIABLE", "VALUE")
            .setEntrypoint(Arrays.asList("some", "command"))
            .setProgramArguments(Arrays.asList("arg1", "arg2"))
            .addExposedPorts(ImmutableSet.of(Port.tcp(1000), Port.tcp(2000)))
            .addVolumes(
                ImmutableSet.of(
                    AbsoluteUnixPath.get("/a/path"), AbsoluteUnixPath.get("/another/path")))
            .setUser("john")
            .addLayer(mockLayer)
            .build();

    Assert.assertEquals(V22ManifestTemplate.class, image.getImageFormat());
    Assert.assertEquals(
        mockDescriptorDigest, image.getLayers().get(0).getBlobDescriptor().getDigest());
    Assert.assertEquals(Instant.ofEpochSecond(10000), image.getCreated());
    Assert.assertEquals(
        ImmutableMap.of("crepecake", "is great", "VARIABLE", "VALUE"), image.getEnvironment());
    Assert.assertEquals(Arrays.asList("some", "command"), image.getEntrypoint());
    Assert.assertEquals(Arrays.asList("arg1", "arg2"), image.getProgramArguments());
    Assert.assertEquals(ImmutableSet.of(Port.tcp(1000), Port.tcp(2000)), image.getExposedPorts());
    Assert.assertEquals(
        ImmutableSet.of(AbsoluteUnixPath.get("/a/path"), AbsoluteUnixPath.get("/another/path")),
        image.getVolumes());
    Assert.assertEquals("john", image.getUser());
  }

  @Test
  void testDefaults() {
    Image image = Image.builder(V22ManifestTemplate.class).build();
    Assert.assertEquals("amd64", image.getArchitecture());
    Assert.assertEquals("linux", image.getOs());
    Assert.assertEquals(Collections.emptyList(), image.getLayers());
    Assert.assertEquals(Collections.emptyList(), image.getHistory());
  }

  @Test
  void testOsArch() {
    Image image =
        Image.builder(V22ManifestTemplate.class).setArchitecture("wasm").setOs("js").build();
    Assert.assertEquals("wasm", image.getArchitecture());
    Assert.assertEquals("js", image.getOs());
  }
}
