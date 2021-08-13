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

import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A class that keeps track of permissions for various stacking file permissions settings in {@link
 * LayerSpec}.
 */
class FilePropertiesStack {

  // TODO perhaps use a fixed size list here
  private final List<FilePropertiesSpec> stack = new ArrayList<>(3);

  private FilePermissions filePermissions;
  private FilePermissions directoryPermissions;
  private Instant modificationTime;
  private String ownership;

  /** Create new FilePropertiesStack with defaults. */
  public FilePropertiesStack() {
    setDefaults();
  }

  private void setDefaults() {
    filePermissions = FilePermissions.DEFAULT_FILE_PERMISSIONS;
    directoryPermissions = FilePermissions.DEFAULT_FOLDER_PERMISSIONS;
    modificationTime = FileEntriesLayer.DEFAULT_MODIFICATION_TIME;
    // TODO: get default from FileEntriesLayer (requires buildplan release)
    ownership = "";
  }

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
    Preconditions.checkState(!stack.isEmpty(), "Error in file properties stack pop, popping at 0");
    stack.remove(stack.size() - 1);
    updateProperties();
  }

  private void updateProperties() {
    // clear existing permissions before recalculating
    setDefaults();

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
    ownership = (user != null ? user : "") + (group != null ? ":" + group : "");
  }

  public FilePermissions getFilePermissions() {
    return filePermissions;
  }

  public FilePermissions getDirectoryPermissions() {
    return directoryPermissions;
  }

  public Instant getModificationTime() {
    return modificationTime;
  }

  public String getOwnership() {
    return ownership;
  }
}
