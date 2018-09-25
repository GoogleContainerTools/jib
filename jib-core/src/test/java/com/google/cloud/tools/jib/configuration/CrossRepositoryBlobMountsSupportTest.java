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

package com.google.cloud.tools.jib.configuration;

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link CrossRepositoryBlobMountsSupport}. */
@RunWith(MockitoJUnitRunner.class)
public class CrossRepositoryBlobMountsSupportTest {

  @Mock JibLogger logger;

  @Test
  public void testSameRegistry() throws InvalidImageReferenceException {
    BuildConfiguration buildConfiguration =
        makeBuildConfiguration("localhost/source", "localhost/destination");
    String result = CrossRepositoryBlobMountsSupport.getMountFrom(buildConfiguration);
    Assert.assertNotNull(result);
    Assert.assertEquals("source", result);
  }

  @Test
  public void testDifferentRegistry() throws InvalidImageReferenceException {
    BuildConfiguration buildConfiguration =
        makeBuildConfiguration("localhost/base", "gcr.io/target");
    String result = CrossRepositoryBlobMountsSupport.getMountFrom(buildConfiguration);
    Assert.assertNull(result);
  }

  private BuildConfiguration makeBuildConfiguration(
      String baseImageReference, String targetImageReference)
      throws InvalidImageReferenceException {
    ImageConfiguration baseConfiguration =
        ImageConfiguration.builder(ImageReference.parse(baseImageReference)).build();
    ImageConfiguration targetConfiguration =
        ImageConfiguration.builder(ImageReference.parse(targetImageReference)).build();
    return BuildConfiguration.builder(logger)
        .setBaseImageConfiguration(baseConfiguration)
        .setTargetImageConfiguration(targetConfiguration)
        .build();
  }
}
