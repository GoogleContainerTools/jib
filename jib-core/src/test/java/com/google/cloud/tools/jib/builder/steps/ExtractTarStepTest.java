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

import com.google.cloud.tools.jib.builder.steps.ExtractTarStep.LocalImage;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.json.BadContainerConfigurationFormatException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ExtractTarStepTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static Path getResource(String resource) throws URISyntaxException {
    return Paths.get(Resources.getResource(resource).toURI());
  }

  @Test
  public void testCall_validDocker()
      throws URISyntaxException, LayerCountMismatchException,
          BadContainerConfigurationFormatException, IOException {
    Path dockerBuild = getResource("core/extraction/docker-save.tar");
    LocalImage result = new ExtractTarStep(dockerBuild, temporaryFolder.getRoot().toPath()).call();

    Assert.assertEquals(2, result.layers.size());
    // TODO: Assert layers are correct

    Assert.assertEquals("value1", result.baseImage.getLabels().get("label1"));
  }

  @Test
  public void testCall_validTar()
      throws URISyntaxException, LayerCountMismatchException,
          BadContainerConfigurationFormatException, IOException {
    Path tarBuild = getResource("core/extraction/jib-image.tar");
    LocalImage result = new ExtractTarStep(tarBuild, temporaryFolder.getRoot().toPath()).call();

    Assert.assertEquals(2, result.layers.size());
    // TODO: Assert layers are correct

    Assert.assertEquals("value1", result.baseImage.getLabels().get("label1"));
  }

  @Test
  public void testCall_noLayers() {}

  @Test
  public void testCall_layerCountMismatch() {}

  @Test
  public void testIsGzipped() throws URISyntaxException, IOException {
    Assert.assertTrue(ExtractTarStep.isGzipped(getResource("core/extraction/compressed.tar.gz")));
    Assert.assertFalse(ExtractTarStep.isGzipped(getResource("core/extraction/not-compressed.tar")));
  }
}
