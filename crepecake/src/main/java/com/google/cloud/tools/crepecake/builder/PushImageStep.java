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

import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.hash.CountingDigestOutputStream;
import com.google.cloud.tools.crepecake.http.Authorization;
import com.google.cloud.tools.crepecake.image.Image;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.crepecake.image.json.ImageToJsonTranslator;
import com.google.cloud.tools.crepecake.image.json.V22ManifestTemplate;
import com.google.cloud.tools.crepecake.registry.RegistryClient;
import com.google.cloud.tools.crepecake.registry.RegistryException;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.util.concurrent.Callable;

/** Pushes the final image. */
class PushImageStep implements Callable<Void> {

  private final BuildConfiguration buildConfiguration;
  private final Authorization pushAuthorization;
  private final Image image;

  PushImageStep(
      BuildConfiguration buildConfiguration, Authorization pushAuthorization, Image image) {
    this.buildConfiguration = buildConfiguration;
    this.pushAuthorization = pushAuthorization;
    this.image = image;
  }

  @Override
  public Void call() throws IOException, RegistryException, LayerPropertyNotFoundException {
    RegistryClient registryClient =
        new RegistryClient(
            pushAuthorization,
            buildConfiguration.getTargetServerUrl(),
            buildConfiguration.getTargetImageName());

    ImageToJsonTranslator imageToJsonTranslator = new ImageToJsonTranslator(image);

    // Pushes the container configuration.
    Blob containerConfigurationBlob = imageToJsonTranslator.getContainerConfigurationBlob();
    CountingDigestOutputStream digestOutputStream =
        new CountingDigestOutputStream(ByteStreams.nullOutputStream());
    containerConfigurationBlob.writeTo(digestOutputStream);
    BlobDescriptor containerConfigurationBlobDescriptor = digestOutputStream.toBlobDescriptor();
    registryClient.pushBlob(
        containerConfigurationBlobDescriptor.getDigest(), containerConfigurationBlob);

    // Pushes the image manifest.
    V22ManifestTemplate manifestTemplate =
        imageToJsonTranslator.getManifestTemplate(containerConfigurationBlobDescriptor);
    registryClient.pushManifest(manifestTemplate, buildConfiguration.getTargetTag());

    return null;
  }
}
