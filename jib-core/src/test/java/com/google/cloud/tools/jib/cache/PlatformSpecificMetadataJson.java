/*
 * Copyright 2018 Google LLC.
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

import com.google.common.io.Resources;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Test utility for using platform-specific test resources. */
public class PlatformSpecificMetadataJson {

  /**
   * Retrieves a sample metadata JSON file. A separate version for Windows is used to account for
   * the different path separator.
   */
  public static Path getMetadataJsonFile() throws URISyntaxException {
    String metadataResourceFilename = "json/metadata-v2.json";
    if (System.getProperty("os.name").toLowerCase().contains("win")) {
      metadataResourceFilename = "json/metadata_windows-v2.json";
    }
    // Loads the expected JSON string.
    return Paths.get(Resources.getResource(metadataResourceFilename).toURI());
  }

  private PlatformSpecificMetadataJson() {}
}
