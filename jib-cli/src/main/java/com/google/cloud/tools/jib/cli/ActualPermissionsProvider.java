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

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.FilePermissionsProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** A permission provider that uses the actual file permissions from the file-system. */
class ActualPermissionsProvider implements FilePermissionsProvider {
  @Override
  public FilePermissions get(Path local, AbsoluteUnixPath inContainer) {
    try {
      return FilePermissions.fromPosixFilePermissions(Files.getPosixFilePermissions(local));
    } catch (IOException ex) {
      throw new RuntimeException(local.toString(), ex);
    }
  }
}
