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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.function.Consumer;

/** Builds a set of event handlers to handle {@link JibEvent}s. */
public class EventHandlers {

  /** Maps from {@link JibEvent} class to handlers for that event type. */
  private final Multimap<Class<? extends JibEvent>, Handler<? extends JibEvent>> handlers =
      ArrayListMultimap.create();

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
    handlers.put(eventClass, new Handler<>(eventClass, eventConsumer));
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
  ImmutableMultimap<Class<? extends JibEvent>, Handler<? extends JibEvent>> getHandlers() {
    return ImmutableMultimap.copyOf(handlers);
  }
}
