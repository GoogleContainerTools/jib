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

package com.google.cloud.tools.jib.cache;

import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.nio.file.Path;
import java.security.DigestException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link CacheFiles}. */
@RunWith(MockitoJUnitRunner.class)
public class CacheFilesTest {

  @Mock private Path mockPath;

  @Test
  public void testGetMetadataFile() {
    ArgumentCaptor<String> fileNameCaptor = ArgumentCaptor.forClass(String.class);

    Mockito.when(mockPath.resolve(fileNameCaptor.capture())).thenReturn(mockPath);

    Path metadataFile = CacheFiles.getMetadataFile(mockPath);

    Assert.assertEquals("metadata-v3.json", fileNameCaptor.getValue());
    Assert.assertEquals(mockPath, metadataFile);
  }

  @Test
  public void testGetLayerFile() throws DigestException {
    DescriptorDigest layerDigest =
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad");

    ArgumentCaptor<String> fileNameCaptor = ArgumentCaptor.forClass(String.class);

    Mockito.when(mockPath.resolve(fileNameCaptor.capture())).thenReturn(mockPath);

    Path layerFile = CacheFiles.getLayerFile(mockPath, layerDigest);

    Assert.assertEquals(
        "8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad.tar.gz",
        fileNameCaptor.getValue());
    Assert.assertEquals(mockPath, layerFile);
  }
}
