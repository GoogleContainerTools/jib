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

import com.google.common.collect.ImmutableMultimap;

/** Emits {@link JibEvent}s to event handlers. */
public class EventEmitter {

  /** Maps from {@link JibEvent} class to handlers for that event type. */
  private final ImmutableMultimap<Class<? extends JibEvent>, Handler<? extends JibEvent>> handlers;

  /**
   * Creates an instance from {@link Handler}s in an {@link EventHandlers}.
   *
   * @param eventHandlers the {@link EventHandlers} to get the {@link Handler}s from
   */
  public EventEmitter(EventHandlers eventHandlers) {
    handlers = eventHandlers.getHandlers();
  }

  /**
   * Emits {@code jibEvent} to all the handlers that can handle it.
   *
   * @param jibEvent the {@link JibEvent} to emit
   */
  public void emit(JibEvent jibEvent) {
    handlers.get(JibEvent.class).forEach(handler -> handler.handle(jibEvent));
    handlers.get(jibEvent.getClass()).forEach(handler -> handler.handle(jibEvent));
  }
}
