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
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.util.List;

/** Translates a list of {@link Image} into a manifest list. */
public class ManifestListGenerator {

  private final BuildContext buildContext;
  private final List<Image> builtImages;

  public ManifestListGenerator(BuildContext buildContext, List<Image> builtImages) {
    this.buildContext = buildContext;
    this.builtImages = builtImages;
  }

  public ManifestTemplate getManifestListTemplate() throws IOException {
    V22ManifestListTemplate manifestList = new V22ManifestListTemplate();

    for (Image builtImage : builtImages) {
      JsonTemplate containerConfiguration =
          new ImageToJsonTranslator(builtImage).getContainerConfiguration();
      BlobDescriptor configDescriptor =
          Blobs.from(containerConfiguration).writeTo(ByteStreams.nullOutputStream());

      BuildableManifestTemplate manifestTemplate =
          new ImageToJsonTranslator(builtImage)
              .getManifestTemplate(buildContext.getTargetFormat(), configDescriptor);
      BlobDescriptor manifestDescriptor =
          Blobs.from(manifestTemplate).writeTo(ByteStreams.nullOutputStream());

      ManifestDescriptorTemplate manifest = new ManifestDescriptorTemplate();
      manifest.setMediaType(manifestTemplate.getManifestMediaType());
      manifest.setSize(manifestDescriptor.getSize());
      manifest.setDigest(manifestDescriptor.getDigest().toString());
      manifest.setPlatform(builtImage.getArchitecture(), builtImage.getOs());
      manifestList.addManifest(manifest);
    }
    return manifestList;
  }
}
