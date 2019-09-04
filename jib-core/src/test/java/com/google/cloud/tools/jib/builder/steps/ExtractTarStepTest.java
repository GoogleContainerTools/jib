/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.ExtractTarStep.LocalImage;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.json.BadContainerConfigurationFormatException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ExtractTarStepTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private ProgressEventDispatcher.Factory progressEventDispatcherFactory;
  @Mock private ProgressEventDispatcher progressEventDispatcher;
  @Mock private ProgressEventDispatcher.Factory childFactory;
  @Mock private ProgressEventDispatcher childDispatcher;

  private static Path getResource(String resource) throws URISyntaxException {
    return Paths.get(Resources.getResource(resource).toURI());
  }

  @Before
  public void setup() {
    Mockito.when(progressEventDispatcherFactory.create(Mockito.anyString(), Mockito.anyLong()))
        .thenReturn(progressEventDispatcher);
    Mockito.when(progressEventDispatcher.newChildProducer()).thenReturn(childFactory);
    Mockito.when(childFactory.create(Mockito.anyString(), Mockito.anyLong()))
        .thenReturn(childDispatcher);
  }

  @Test
  public void testCall_validDocker()
      throws URISyntaxException, LayerCountMismatchException,
          BadContainerConfigurationFormatException, IOException {
    Path dockerBuild = getResource("core/extraction/docker-save.tar");
    LocalImage result =
        new ExtractTarStep(
                dockerBuild, temporaryFolder.getRoot().toPath(), progressEventDispatcherFactory)
            .call();

    Mockito.verify(progressEventDispatcher, Mockito.times(2)).newChildProducer();
    Assert.assertEquals(2, result.layers.size());
    Assert.assertEquals(
        "5e701122d3347fae0758cd5b7f0692c686fcd07b0e7fd9c4a125fbdbbedc04dd",
        result.layers.get(0).getDiffId().getHash());
    Assert.assertEquals(
        "0011328ac5dfe3dde40c7c5e0e00c98d1833a3aeae2bfb668cf9eb965c229c7f",
        result.layers.get(0).getBlobDescriptor().getDigest().getHash());
    Assert.assertEquals(
        "f1ac3015bcbf0ada4750d728626eb10f0f585199e2b667dcd79e49f0e926178e",
        result.layers.get(1).getDiffId().getHash());
    Assert.assertEquals(
        "c10ef24a5cef5092bbcb5a5666721cff7b86ce978c203a958d1fc86ee6c19f94",
        result.layers.get(1).getBlobDescriptor().getDigest().getHash());
    Assert.assertEquals("value1", result.baseImage.getLabels().get("label1"));
  }

  @Test
  public void testCall_validTar()
      throws URISyntaxException, LayerCountMismatchException,
          BadContainerConfigurationFormatException, IOException {
    Path tarBuild = getResource("core/extraction/jib-image.tar");
    LocalImage result =
        new ExtractTarStep(
                tarBuild, temporaryFolder.getRoot().toPath(), progressEventDispatcherFactory)
            .call();

    Mockito.verify(progressEventDispatcher, Mockito.times(2)).newChildProducer();
    Assert.assertEquals(2, result.layers.size());
    Assert.assertEquals(
        "5e701122d3347fae0758cd5b7f0692c686fcd07b0e7fd9c4a125fbdbbedc04dd",
        result.layers.get(0).getDiffId().getHash());
    Assert.assertEquals(
        "0011328ac5dfe3dde40c7c5e0e00c98d1833a3aeae2bfb668cf9eb965c229c7f",
        result.layers.get(0).getBlobDescriptor().getDigest().getHash());
    Assert.assertEquals(
        "f1ac3015bcbf0ada4750d728626eb10f0f585199e2b667dcd79e49f0e926178e",
        result.layers.get(1).getDiffId().getHash());
    Assert.assertEquals(
        "c10ef24a5cef5092bbcb5a5666721cff7b86ce978c203a958d1fc86ee6c19f94",
        result.layers.get(1).getBlobDescriptor().getDigest().getHash());
    Assert.assertEquals("value1", result.baseImage.getLabels().get("label1"));
  }

  @Test
  public void testIsGzipped() throws URISyntaxException, IOException {
    Assert.assertTrue(ExtractTarStep.isGzipped(getResource("core/extraction/compressed.tar.gz")));
    Assert.assertFalse(ExtractTarStep.isGzipped(getResource("core/extraction/not-compressed.tar")));
  }
}
