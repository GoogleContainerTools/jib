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

package com.google.cloud.tools.jib.event;

import com.google.common.annotations.VisibleForTesting;

/** Holds references to all {@link JibEvent} types. */
public class JibEventType<E extends JibEvent> {

  /** All event types. Handlers for this will always be called first. */
  public static final JibEventType<JibEvent> ALL = new JibEventType<>(JibEvent.class);

  // TODO: Add entries for all JibEvent types.

  private final Class<E> eventClass;

  @VisibleForTesting
  JibEventType(Class<E> eventClass) {
    this.eventClass = eventClass;
  }

  Class<E> getEventClass() {
    return eventClass;
  }
}
