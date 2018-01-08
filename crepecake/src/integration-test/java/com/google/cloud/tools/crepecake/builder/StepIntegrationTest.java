/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.crepecake.builder;

import com.google.cloud.tools.crepecake.cache.Cache;
import com.google.cloud.tools.crepecake.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.crepecake.cache.CachedLayer;
import com.google.cloud.tools.crepecake.http.Authorization;
import com.google.cloud.tools.crepecake.image.DuplicateLayerException;
import com.google.cloud.tools.crepecake.image.Image;
import com.google.cloud.tools.crepecake.image.ImageLayers;
import com.google.cloud.tools.crepecake.image.LayerCountMismatchException;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.crepecake.registry.LocalRegistry;
import com.google.cloud.tools.crepecake.registry.RegistryAuthenticationFailedException;
import com.google.cloud.tools.crepecake.registry.RegistryException;
import java.io.IOException;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Integration tests for various {@link Step}s. */
public class StepIntegrationTest {

  @ClassRule public static LocalRegistry localRegistry = new LocalRegistry(5000);

  @Rule public TemporaryFolder temporaryCacheDirectory = new TemporaryFolder();

  @Test
  public void testSteps()
      throws DuplicateLayerException, LayerPropertyNotFoundException, RegistryException,
          LayerCountMismatchException, IOException, CacheMetadataCorruptedException,
          RegistryAuthenticationFailedException {
    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder()
            .setBaseImageServerUrl("registry.hub.docker.com")
            .setBaseImageName("frolvlad/alpine-oraclejdk8")
            .setBaseImageTag("latest")
            .setTargetServerUrl("localhost:5000")
            .setTargetImageName("testimage")
            .setTargetTag("testtag")
            .build();
    Cache cache = Cache.init(temporaryCacheDirectory.newFolder().toPath());

    AuthenticatePullStep authenticatePullStep = new AuthenticatePullStep(buildConfiguration);
    Authorization pullAuthorization = authenticatePullStep.run(null);

    PullBaseImageStep pullBaseImageStep =
        new PullBaseImageStep(buildConfiguration, pullAuthorization);
    Image baseImage = pullBaseImageStep.run(null);

    PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep =
        new PullAndCacheBaseImageLayersStep(buildConfiguration, cache, pullAuthorization);
    ImageLayers<CachedLayer> baseImageLayers = pullAndCacheBaseImageLayersStep.run(baseImage);

    for (CachedLayer cachedLayer : baseImageLayers) {
      System.out.println(
          "CachedLayer(file:"
              + cachedLayer.getContentFile()
              + ", digest:"
              + cachedLayer.getBlobDescriptor().getDigest()
              + ", diffId:"
              + cachedLayer.getDiffId());
    }

    // TODO: Integrate any new steps as they are added.
  }
}
