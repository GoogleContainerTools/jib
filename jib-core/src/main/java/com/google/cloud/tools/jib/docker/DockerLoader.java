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

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.blob.Blob;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class DockerLoader {

  public void load(Blob image, String name) throws IOException, InterruptedException {
    File tempFile = new File(System.getProperty("java.io.tmpdir"), name);
    tempFile.deleteOnExit();
    image.writeTo(new BufferedOutputStream(Files.newOutputStream(tempFile.toPath())));
    new Command("docker", "load", "--input", name).run();
  }
}
