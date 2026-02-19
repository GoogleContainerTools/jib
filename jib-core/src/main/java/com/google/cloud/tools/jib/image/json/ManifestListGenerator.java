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

import com.google.api.client.util.Preconditions;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.image.Image;
import java.io.IOException;
import java.util.List;

/** Generates a manifest list for {@link Image}s. */
public class ManifestListGenerator {

  private final List<Image> images;

  public ManifestListGenerator(List<Image> images) {
    this.images = images;
  }

  /**
   * Generates a manifest list JSON for the given {@link Image}s.
   *
   * @param <T> child type of {@link BuildableManifestTemplate}
   * @param manifestTemplateClass the JSON template to translate the image to
   * @return a manifest list JSON
   * @throws IOException if generating a manifest list fails due to an I/O error when computing
   *     digests
   */
  public <T extends BuildableManifestTemplate> ManifestTemplate getManifestListTemplate(
      Class<T> manifestTemplateClass) throws IOException {
    Preconditions.checkState(!images.isEmpty(), "no images given");

    if (manifestTemplateClass == V22ManifestTemplate.class) {
      return getV22ManifestListTemplate();

    } else if (manifestTemplateClass == OciManifestTemplate.class) {
      return getOciIndexTemplate();
    }
    throw new IllegalArgumentException(
        "Unsupported manifestTemplateClass format " + manifestTemplateClass);
  }

  private V22ManifestListTemplate getV22ManifestListTemplate() throws IOException {
    V22ManifestListTemplate manifestList = new V22ManifestListTemplate();
    for (Image image : images) {
      BuildableManifestTemplate manifestTemplate =
          getBuildableManifestTemplate(V22ManifestTemplate.class, image);
      BlobDescriptor manifestDescriptor = Digests.computeDigest(manifestTemplate);

      V22ManifestListTemplate.ManifestDescriptorTemplate manifest =
          new V22ManifestListTemplate.ManifestDescriptorTemplate();
      manifest.setMediaType(manifestTemplate.getManifestMediaType());
      manifest.setSize(manifestDescriptor.getSize());
      manifest.setDigest(manifestDescriptor.getDigest().toString());
      manifest.setPlatform(image.getArchitecture(), image.getOs());
      manifestList.addManifest(manifest);
    }
    return manifestList;
  }

  private OciIndexTemplate getOciIndexTemplate() throws IOException {
    OciIndexTemplate manifestList = new OciIndexTemplate();
    for (Image image : images) {
      BuildableManifestTemplate manifestTemplate =
          getBuildableManifestTemplate(OciManifestTemplate.class, image);
      BlobDescriptor manifestDescriptor = Digests.computeDigest(manifestTemplate);

      OciIndexTemplate.ManifestDescriptorTemplate manifest =
          new OciIndexTemplate.ManifestDescriptorTemplate(
              manifestTemplate.getManifestMediaType(),
              manifestDescriptor.getSize(),
              manifestDescriptor.getDigest());
      manifest.setPlatform(image.getArchitecture(), image.getOs());
      manifestList.addManifest(manifest);
    }
    return manifestList;
  }

  private <T extends BuildableManifestTemplate>
      BuildableManifestTemplate getBuildableManifestTemplate(
          Class<T> manifestTemplateClass, Image image) throws IOException {
    ImageToJsonTranslator imageTranslator = new ImageToJsonTranslator(image);
    BlobDescriptor configDescriptor =
        Digests.computeDigest(imageTranslator.getContainerConfiguration());
    return imageTranslator.getManifestTemplate(manifestTemplateClass, configDescriptor);
  }
}
