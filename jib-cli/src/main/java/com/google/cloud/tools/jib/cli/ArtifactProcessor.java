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

package com.google.cloud.tools.jib.cli;

import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;

/** Interface to create layers and compute entrypoint from JAR or WAR file contents. */
public interface ArtifactProcessor {

  /**
   * Creates layers on container for a JAR or WAR.
   *
   * @return list of {@link FileEntriesLayer}
   * @throws IOException if I/O error occurs when opening the java artifact or if temporary
   *     directory provided doesn't exist
   */
  List<FileEntriesLayer> createLayers() throws IOException;

  /**
   * Computes the entrypoint for a JAR or WAR.
   *
   * @param jvmFlags list of jvm flags
   * @return list of {@link String} representing entrypoint
   * @throws IOException if I/O error occurs when opening the java artifact
   */
  ImmutableList<String> computeEntrypoint(List<String> jvmFlags) throws IOException;

  Integer getJavaVersion();
}
