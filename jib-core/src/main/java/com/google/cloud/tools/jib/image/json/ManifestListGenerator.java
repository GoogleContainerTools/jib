/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.image.json;

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate.ManifestDescriptorTemplate;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/** Translates a list of {@link Image} into a manifest list. */
public class ManifestListGenerator {

  private final BuildContext buildContext;
  private final List<Image> builtImages;
  private final List<BlobDescriptor> containerConfigPushResults;

  public ManifestListGenerator(
      BuildContext buildContext,
      List<Image> builtImages,
      List<BlobDescriptor> containerConfigPushResults) {
    this.buildContext = buildContext;
    this.builtImages = builtImages;
    this.containerConfigPushResults = containerConfigPushResults;
  }

  /**
   * Gets the manifest as a JSON template. The {@code containerConfigurationBlobDescriptor} must be
   * the {@link BlobDescriptor} obtained by writing out the container configuration JSON returned
   * from {@link #getContainerConfiguration()}.
   *
   * @param <T> child type of {@link BuildableManifestTemplate}.
   * @param manifestTemplateClass the JSON template to translate the image to.
   * @param containerConfigurationBlobDescriptor the container configuration descriptor.
   * @return the image contents serialized as JSON.
   * @throws IOException
   */
  public V22ManifestListTemplate getManifestListTemplate() throws IOException {
    V22ManifestListTemplate manifestList = new V22ManifestListTemplate();

    Iterator<Image> builtImage = builtImages.iterator();
    Iterator<BlobDescriptor> containerConfigPushResult = containerConfigPushResults.iterator();

    while (builtImage.hasNext() && containerConfigPushResult.hasNext()) {
      // Gets the image manifest.
      BuildableManifestTemplate manifestTemplate =
          new ImageToJsonTranslator(builtImage.next())
              .getManifestTemplate(
                  buildContext.getTargetFormat(), containerConfigPushResult.next());

      BlobDescriptor configDescriptor =
          Blobs.from(manifestTemplate).writeTo(ByteStreams.nullOutputStream());

      ManifestDescriptorTemplate manifest = new ManifestDescriptorTemplate();
      manifest.setSize(configDescriptor.getSize());
      manifest.setDigest(configDescriptor.getDigest().toString());
      manifest.setPlatform(builtImage.next().getArchitecture(), builtImage.next().getOs());
      manifestList.addManifest(manifest);
    }
    return manifestList;
  }
}
