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

import com.google.common.base.Preconditions;
import java.util.function.Consumer;

/** Handles an emitted {@link JibEvent}. */
class Handler<E extends JibEvent> {

  private final Class<E> eventClass;
  private final Consumer<E> eventConsumer;

  Handler(Class<E> eventClass, Consumer<E> eventConsumer) {
    this.eventClass = eventClass;
    this.eventConsumer = eventConsumer;
  }

  /**
   * Handles a {@link JibEvent}.
   *
   * @param jibEvent the event to handle
   */
  void handle(JibEvent jibEvent) {
    Preconditions.checkArgument(eventClass.isInstance(jibEvent));
    eventConsumer.accept(eventClass.cast(jibEvent));
  }
}
