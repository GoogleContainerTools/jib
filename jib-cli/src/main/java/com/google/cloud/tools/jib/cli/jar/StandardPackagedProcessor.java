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

package com.google.cloud.tools.jib.cli.jar;

import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.cli.ArtifactProcessor;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public class StandardPackagedProcessor implements ArtifactProcessor {

  private final Path jarPath;
  private final Integer jarJavaVersion;

  /**
   * Constructor for {@link StandardPackagedProcessor}.
   *
   * @param jarPath path to jar file
   * @param jarJavaVersion jar java version
   */
  public StandardPackagedProcessor(Path jarPath, Integer jarJavaVersion) {
    this.jarPath = jarPath;
    this.jarJavaVersion = jarJavaVersion;
  }

  @Override
  public List<FileEntriesLayer> createLayers() throws IOException {
    // Add dependencies layers.
    List<FileEntriesLayer> layers =
        JarLayers.getDependenciesLayers(jarPath, ProcessingMode.packaged);

    // Add layer for jar.
    FileEntriesLayer jarLayer =
        FileEntriesLayer.builder()
            .setName(JarLayers.JAR)
            .addEntry(jarPath, JarLayers.APP_ROOT.resolve(jarPath.getFileName()))
            .build();
    layers.add(jarLayer);

    return layers;
  }

  @Override
  public ImmutableList<String> computeEntrypoint(List<String> jvmFlags) throws IOException {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      String mainClass =
          jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
      if (mainClass == null) {
        throw new IllegalArgumentException(
            "`Main-Class:` attribute for an application main class not defined in the input JAR's "
                + "manifest (`META-INF/MANIFEST.MF` in the JAR).");
      }
      ImmutableList.Builder<String> entrypoint = ImmutableList.builder();
      entrypoint.add("java");
      entrypoint.addAll(jvmFlags);
      entrypoint.add("-jar");
      entrypoint.add(JarLayers.APP_ROOT + "/" + jarPath.getFileName().toString());
      return entrypoint.build();
    }
  }

  @Override
  public Integer getJavaVersion() {
    return jarJavaVersion;
  }
}
