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

package com.google.cloud.tools.jib.api;
// TODO: Move to com.google.cloud.tools.jib once that package is cleaned up.

import com.google.cloud.tools.jib.configuration.CacheConfiguration;
import com.google.cloud.tools.jib.event.EventHandlers;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;

/** Configures how to containerize. */
// TODO: Add tests once JibContainerBuilder#containerize() is added.
public class Containerizer {

  /**
   * Gets a new {@link Containerizer} that containerizes to a container registry.
   *
   * @param registryImage the {@link RegistryImage} that defines target container registry and
   *     credentials
   * @return a new {@link Containerizer}
   */
  public static Containerizer to(RegistryImage registryImage) {
    return new Containerizer(registryImage);
  }

  /**
   * Gets a new {@link Containerizer} that containerizes to a Docker daemon.
   *
   * @param dockerDaemonImage the {@link DockerDaemonImage} that defines target Docker daemon
   * @return a new {@link Containerizer}
   */
  public static Containerizer to(DockerDaemonImage dockerDaemonImage) {
    return new Containerizer(dockerDaemonImage);
  }

  /**
   * Gets a new {@link Containerizer} that containerizes to a tarball archive.
   *
   * @param tarImage the {@link TarImage} that defines target output file
   * @return a new {@link Containerizer}
   */
  public static Containerizer to(TarImage tarImage) {
    return new Containerizer(tarImage);
  }

  private final TargetImage targetImage;
  @Nullable private ExecutorService executorService;
  @Nullable private CacheConfiguration cacheConfiguration;
  @Nullable private EventHandlers eventHandlers;

  /** Instantiate with {@link #to}. */
  private Containerizer(TargetImage targetImage) {
    this.targetImage = targetImage;
  }

  /**
   * Sets the {@link ExecutorService} Jib executes on. Jib, by default, uses {@link
   * Executors#newCachedThreadPool}.
   *
   * @param executorService the {@link ExecutorService}
   * @return this
   */
  public Containerizer setExecutorService(@Nullable ExecutorService executorService) {
    this.executorService = executorService;
    return this;
  }

  // TODO: Rethink this method.
  public Containerizer setCacheConfiguration(CacheConfiguration cacheConfiguration) {
    this.cacheConfiguration = cacheConfiguration;
    return this;
  }

  /**
   * Sets the {@link EventHandlers} to handle events dispatched during Jib's execution.
   *
   * @param eventHandlers the {@link EventHandlers}
   * @return this
   */
  public Containerizer setEventHandlers(EventHandlers eventHandlers) {
    this.eventHandlers = eventHandlers;
    return this;
  }

  TargetImage getTargetImage() {
    return targetImage;
  }

  Optional<ExecutorService> getExecutorService() {
    return Optional.ofNullable(executorService);
  }

  Optional<CacheConfiguration> getCacheConfiguration() {
    return Optional.ofNullable(cacheConfiguration);
  }

  Optional<EventHandlers> getEventHandlers() {
    return Optional.ofNullable(eventHandlers);
  }
}
