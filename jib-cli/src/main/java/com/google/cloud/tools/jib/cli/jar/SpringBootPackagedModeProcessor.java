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

public class SpringBootPackagedModeProcessor implements JarModeProcessor {

  @Override
  public List<FileEntriesLayer> createLayers(Path jarPath) {
    FileEntriesLayer jarLayer =
        FileEntriesLayer.builder()
            .setName(JarProcessorHelper.JAR)
            .addEntry(jarPath, JarProcessorHelper.APP_ROOT.resolve(jarPath.getFileName()))
            .build();
    return Collections.singletonList(jarLayer);
  }

  @Override
  public ImmutableList<String> computeEntrypoint(Path jarPath) {
    return ImmutableList.of(
        "java", "-jar", JarProcessorHelper.APP_ROOT + "/" + jarPath.getFileName().toString());
  }
}
