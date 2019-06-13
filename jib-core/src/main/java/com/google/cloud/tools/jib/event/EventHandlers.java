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

import com.google.cloud.tools.jib.api.JibEvent;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.function.Consumer;

/** Builds a set of event handlers to handle {@link JibEvent}s. */
public class EventHandlers {

  /** Builder for {@link EventHandlers}. */
  public static class Builder {

    private final Multimap<Class<? extends JibEvent>, Handler<? extends JibEvent>> handlers =
        ArrayListMultimap.create();

    /**
     * Adds the {@code eventConsumer} to handle the {@link JibEvent} with class {@code eventClass}.
     * The order in which handlers are added is the order in which they are called when the event is
     * dispatched.
     *
     * <p><b>Note: Implementations of {@code eventConsumer} must be thread-safe.</b>
     *
     * @param eventType the event type that {@code eventConsumer} should handle
     * @param eventConsumer the event handler
     * @param <E> the type of {@code eventClass}
     * @return this
     */
    public <E extends JibEvent> Builder add(Class<E> eventType, Consumer<? super E> eventConsumer) {
      handlers.put(eventType, new Handler<>(eventType, eventConsumer));
      return this;
    }

    public EventHandlers build() {
      return new EventHandlers(handlers);
    }
  }

  /** An empty {@link EventHandlers}. */
  public static final EventHandlers NONE = new Builder().build();

  /** Maps from {@link JibEvent} class to handlers for that event type. */
  private final ImmutableMultimap<Class<? extends JibEvent>, Handler<? extends JibEvent>> handlers;

  private EventHandlers(Multimap<Class<? extends JibEvent>, Handler<? extends JibEvent>> handlers) {
    this.handlers = ImmutableMultimap.copyOf(handlers);
  }

  /**
   * Creates a new {@link EventHandlers.Builder}.
   *
   * @return the builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Dispatches {@code jibEvent} to all the handlers that can handle it.
   *
   * @param jibEvent the {@link JibEvent} to dispatch
   */
  public void dispatch(JibEvent jibEvent) {
    if (handlers.isEmpty()) {
      return;
    }
    handlers.get(JibEvent.class).forEach(handler -> handler.handle(jibEvent));
    handlers.get(jibEvent.getClass()).forEach(handler -> handler.handle(jibEvent));
  }

  /**
   * Gets the handlers added.
   *
   * @return the map from {@link JibEvent} type to a list of {@link Handler}s
   */
  @VisibleForTesting
  ImmutableMultimap<Class<? extends JibEvent>, Handler<? extends JibEvent>> getHandlers() {
    return ImmutableMultimap.copyOf(handlers);
  }
}
