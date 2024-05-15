/*
 * Copyright 2022 Google LLC.
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

import com.google.cloud.tools.jib.api.DockerClient;
import com.google.cloud.tools.jib.api.ImageDetails;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.image.ImageTarball;
import com.google.cloud.tools.jib.json.JsonTemplate;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

public class AnotherDockerClient implements DockerClient {
  @Override
  public boolean supported(Map<String, String> parameters) {
    return parameters.containsKey("test");
  }

  @Override
  public String load(ImageTarball imageTarball, Consumer<Long> writtenByteCountListener)
      throws InterruptedException, IOException {
    return null;
  }

  @Override
  public void save(
      ImageReference imageReference, Path outputPath, Consumer<Long> writtenByteCountListener)
      throws InterruptedException, IOException {}

  @Override
  public ImageDetails inspect(ImageReference imageReference)
      throws IOException, InterruptedException {
    return null;
  }

  @Override
  public JsonTemplate info() throws IOException, InterruptedException {
    return null;
  }
}
