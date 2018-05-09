/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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

package com.google.cloud.tools.jib.docker;

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import java.util.Collections;
import java.util.List;

/** Utility for creating image manifest for build to Docker daemon. */
public class DockerLoadManifestBlob {

  /**
   * Builds a {@link DockerLoadManifestTemplate} from image parameters and returns the result as a
   * blob.
   */
  public static Blob get(ImageReference imageReference, List<String> layerFiles) {
    // Set up the JSON template.
    DockerLoadManifestTemplate template = new DockerLoadManifestTemplate();
    template.setRepoTags(imageReference.getRepository() + ":" + imageReference.getTag());
    template.addLayerFiles(layerFiles);

    // Serializes into JSON.
    return JsonTemplateMapper.toBlob(Collections.singletonList(template));
  }
}
