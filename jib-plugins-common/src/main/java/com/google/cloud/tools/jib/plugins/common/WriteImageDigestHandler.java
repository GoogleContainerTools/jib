/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.event.events.ImageCreatedEvent;
import com.google.cloud.tools.jib.event.events.LogEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Writes out the image digest to the configured file on an image-creation event. */
public class WriteImageDigestHandler implements Consumer<ImageCreatedEvent> {

  private final Path digestPath;
  private final Consumer<LogEvent> logger;

  public WriteImageDigestHandler(Path digestPath, Consumer<LogEvent> logger) {
    this.digestPath = digestPath;
    this.logger = logger;
  }

  @Override
  public void accept(ImageCreatedEvent event) {
    String imageDigest = event.getImageDigest().toString();
    try {
      Files.write(digestPath, imageDigest.getBytes(StandardCharsets.UTF_8));
    } catch (IOException ex) {
      logger.accept(LogEvent.error("unable to write image digest to " + digestPath + ": " + ex));
    }
  }
}
