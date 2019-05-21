/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.api;

/** Event used for counting layers processed during a build step. */
public class LayerCountEvent implements JibEvent {

  private final BuildStepType buildStepType;
  private final int count;

  /**
   * Creates a new {@link LayerCountEvent}. For internal use only.
   *
   * @param buildStepType the build step associated with the event
   * @param count the number of layers counted
   */
  public LayerCountEvent(BuildStepType buildStepType, int count) {
    this.buildStepType = buildStepType;
    this.count = count;
  }

  /**
   * Gets the type of build step that fired the event.
   *
   * @return the type of build step that fired the event
   */
  public BuildStepType getBuildStepType() {
    return buildStepType;
  }

  /**
   * Gets the number of layers.
   *
   * @return the number of layers
   */
  public int getCount() {
    return count;
  }
}
