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
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

public class SpringBootPackagedProcessor implements JarProcessor {

  @Nullable private final Path jarPath;

  public SpringBootPackagedProcessor(Path jarPath) {
    this.jarPath = jarPath;
  }

  @Override
  public List<FileEntriesLayer> createLayers() {
    if (jarPath == null) {
      return Collections.emptyList();
    }
    FileEntriesLayer jarLayer =
        FileEntriesLayer.builder()
            .setName(JarLayers.JAR)
            .addEntry(jarPath, JarLayers.APP_ROOT.resolve(jarPath.getFileName()))
            .build();
    return Collections.singletonList(jarLayer);
  }

  @Override
  public ImmutableList<String> computeEntrypoint() {
    if (jarPath == null) {
      return ImmutableList.of();
    }
    return ImmutableList.of(
        "java", "-jar", JarLayers.APP_ROOT + "/" + jarPath.getFileName().toString());
  }
}
