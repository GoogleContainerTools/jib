/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.event.events;

import com.google.cloud.tools.jib.api.JibEvent;
import com.google.cloud.tools.jib.event.progress.Allocation;

/**
 * Event representing progress. The progress accounts for allocation units in an {@link Allocation},
 * which makes up a Decentralized Allocation Tree.
 *
 * @see Allocation
 */
public class ProgressEvent implements JibEvent {

  /**
   * The allocation this progress is for. Each progress unit accounts for a single allocation unit
   * on the {@link Allocation}.
   */
  private final Allocation allocation;

  /** Units of progress. */
  private final long progressUnits;

  public ProgressEvent(Allocation allocation, long progressUnits) {
    this.allocation = allocation;
    this.progressUnits = progressUnits;
  }

  /**
   * Gets the {@link Allocation} this progress event accounts for.
   *
   * @return the {@link Allocation}
   */
  public Allocation getAllocation() {
    return allocation;
  }

  /**
   * Gets the units of progress this progress event accounts for in the associated {@link
   * Allocation}.
   *
   * @return units of allocation
   */
  public long getUnits() {
    return progressUnits;
  }
}
