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

package com.google.cloud.tools.jib.cli.buildfile;

import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A class that keeps track of permissions for various stacking file permissions settings in {@link
 * LayerSpec}.
 */
@VisibleForTesting
class FilePropertiesStack {

  // TODO perhaps use a fixed size list here
  private final List<FilePropertiesSpec> stack = new ArrayList<>(3);

  @Nullable private FilePermissions filePermissions;
  @Nullable private FilePermissions directoryPermissions;
  @Nullable private Instant modificationTime;
  @Nullable private String ownership;

  /**
   * Add a new layer to the file properties stack. When adding a new layer, it is given highest
   * priority when resolving properties. All values are recalculated.
   */
  public void push(FilePropertiesSpec filePropertiesSpec) {
    Preconditions.checkState(
        stack.size() < 3, "Error in file properties stack push, stacking over 3");
    stack.add(filePropertiesSpec);
    updateProperties();
  }

  /** Remove the last layer from the stack. All values are recalculated. */
  public void pop() {
    Preconditions.checkState(stack.size() > 0, "Error in file properties stack pop, popping at 0");
    stack.remove(stack.size() - 1);
    updateProperties();
  }

  private void updateProperties() {
    // clear existing permissions before recalculating
    filePermissions = null;
    directoryPermissions = null;
    modificationTime = null;
    ownership = null;

    String user = null;
    String group = null;

    // the item with the lowest index has the lowest priority
    for (FilePropertiesSpec properties : stack) {
      filePermissions = properties.getFilePermissions().orElse(filePermissions);
      directoryPermissions = properties.getDirectoryPermissions().orElse(directoryPermissions);
      modificationTime = properties.getTimestamp().orElse(modificationTime);
      user = properties.getUser().orElse(user);
      group = properties.getGroup().orElse(group);
    }
    // ownership calculations
    if (group == null) {
      ownership = user;
    } else if (user == null) {
      ownership = ":" + group;
    } else {
      ownership = user + ":" + group;
    }
  }

  @Nullable
  public FilePermissions getFilePermissions() {
    return filePermissions;
  }

  @Nullable
  public FilePermissions getDirectoryPermissions() {
    return directoryPermissions;
  }

  @Nullable
  public Instant getModificationTime() {
    return modificationTime;
  }

  @Nullable
  public String getOwnership() {
    return ownership;
  }
}
