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

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.List;

/** Emits {@link JibEvent}s to event handlers. */
public class EventEmitter {

  /** Maps from {@link JibEvent} class to handlers for that event type. */
  private final ImmutableMap<Class<? extends JibEvent>, List<Handler<? extends JibEvent>>> handlers;

  /**
   * Creates from {@link Handler}s in an {@link EventHandlers}.
   *
   * @param eventHandlers the {@link EventHandlers} to get the {@link Handler}s from
   */
  public EventEmitter(EventHandlers eventHandlers) {
    handlers = ImmutableMap.copyOf(eventHandlers.getHandlers());
    System.out.println(handlers);
  }

  /**
   * Emits {@code jibEvent} to all the handlers that can handle it.
   *
   * @param jibEvent the {@link JibEvent} to emit
   */
  public void emit(JibEvent jibEvent) {
    for (Handler<? extends JibEvent> handler : getHandlersFor(JibEvent.class)) {
      handler.handle(jibEvent);
    }
    for (Handler<? extends JibEvent> handler : getHandlersFor(jibEvent.getClass())) {
      handler.handle(jibEvent);
    }
  }

  private List<Handler<? extends JibEvent>> getHandlersFor(
      Class<? extends JibEvent> jibEventClass) {
    List<Handler<? extends JibEvent>> handlerList = handlers.get(jibEventClass);
    if (handlerList == null) {
      return Collections.emptyList();
    }
    return handlerList;
  }
}
