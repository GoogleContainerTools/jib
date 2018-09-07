/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Builds a set of event handlers to handle {@link JibEvent}s. */
public class EventHandlers {

  /** Handles an emitted {@link JibEvent}. */
  public static class Handler<E extends JibEvent> {

    private final Class<E> eventClass;
    private final Consumer<E> eventConsumer;

    private Handler(Class<E> eventClass, Consumer<E> eventConsumer) {
      this.eventClass = eventClass;
      this.eventConsumer = eventConsumer;
    }

    /**
     * Handles a {@link JibEvent}.
     *
     * @param jibEvent the event to handle
     * @return {@code true} if this {@link Handler} handled the event; {@code false} if this {@link
     *     Handler} does not handle the event
     */
    public boolean handle(JibEvent jibEvent) {
      if (eventClass.isInstance(jibEvent)) {
        eventConsumer.accept(eventClass.cast(jibEvent));
        return true;
      }
      return false;
    }
  }

  // Maps from JibEvent class to handlers for that event type.
  private final Map<Class<? extends JibEvent>, List<Handler<? extends JibEvent>>> handlers =
      new HashMap<>();

  /**
   * Adds the {@code eventConsumer} to handle the {@link JibEvent} with class {@code eventClass}.
   * The order in which handlers are added is the order in which they are called when the event is
   * emitted.
   *
   * <p><b>Note: Implementations of {@code eventConsumer} must be thread-safe.</b>
   *
   * @param eventType the event type that {@code eventConsumer} should handle
   * @param eventConsumer the event handler
   * @param <E> the type of {@code eventClass}
   * @return this
   */
  public <E extends JibEvent> EventHandlers add(
      JibEventType<E> eventType, Consumer<E> eventConsumer) {
    Class<E> eventClass = eventType.getEventClass();
    if (!handlers.containsKey(eventClass)) {
      handlers.put(eventClass, new ArrayList<>());
    }
    handlers.get(eventClass).add(new Handler<>(eventClass, eventConsumer));
    return this;
  }

  /**
   * Adds the {@code eventConsumer} to handle all {@link JibEvent} types. See {@link
   * #add(JibEventType, Consumer)} for more details.
   *
   * @param eventConsumer the event handler
   * @return this
   */
  public EventHandlers add(Consumer<JibEvent> eventConsumer) {
    return add(JibEventType.ALL, eventConsumer);
  }

  /**
   * Gets the handlers added.
   *
   * @return the map from {@link JibEvent} type to a list of {@link Handler}s
   */
  public Map<Class<? extends JibEvent>, List<Handler<? extends JibEvent>>> getHandlers() {
    return Collections.unmodifiableMap(handlers);
  }
}
