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

package com.google.cloud.tools.jib.gradle;

import com.google.common.base.Preconditions;
import javax.annotation.Nullable;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

/**
 * Plugin extension for {@link JibPlugin}.
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * jib {
 *   from {
 *     image = ‘gcr.io/my-gcp-project/my-base-image’
 *     credHelper = ‘gcr’
 *   }
 *   to {
 *     image = ‘gcr.io/gcp-project/my-app:built-with-jib’
 *     credHelper = ‘ecr-login’
 *   }
 *   jvmFlags = [‘-Xms512m’, ‘-Xdebug’]
 *   mainClass = ‘com.mycompany.myproject.Main’
 *   args = ['arg1', 'arg2']
 *   format = OCI
 * }
 * }</pre>
 */
public class JibExtension {

  // Defines default configuration values.
  private static final String DEFAULT_FROM_IMAGE = "gcr.io/distroless/java";
  private static final boolean DEFAULT_USE_ONLY_PROJECT_CACHE = false;

  private final ImageConfiguration from;
  private final ImageConfiguration to;
  private final ContainerConfiguration container;
  private final Property<Boolean> useOnlyProjectCache;

  public JibExtension(Project project) {
    ObjectFactory objectFactory = project.getObjects();

    from = objectFactory.newInstance(ImageConfiguration.class);
    to = objectFactory.newInstance(ImageConfiguration.class);
    container = objectFactory.newInstance(ContainerConfiguration.class);

    useOnlyProjectCache = objectFactory.property(Boolean.class);

    // Sets defaults.
    from.setImage(DEFAULT_FROM_IMAGE);
    useOnlyProjectCache.set(DEFAULT_USE_ONLY_PROJECT_CACHE);
  }

  public void from(Action<? super ImageConfiguration> action) {
    action.execute(from);
  }

  public void to(Action<? super ImageConfiguration> action) {
    action.execute(to);
  }

  public void container(Action<? super ContainerConfiguration> action) {
    action.execute(container);
  }

  public void setUseOnlyProjectCache(boolean useOnlyProjectCache) {
    this.useOnlyProjectCache.set(useOnlyProjectCache);
  }

  @Internal
  String getBaseImage() {
    return Preconditions.checkNotNull(from.getImage());
  }

  @Internal
  @Nullable
  String getTargetImage() {
    return to.getImage();
  }

  @Nested
  @Optional
  ImageConfiguration getFrom() {
    return from;
  }

  @Nested
  @Optional
  ImageConfiguration getTo() {
    return to;
  }

  @Nested
  @Optional
  ContainerConfiguration getContainer() {
    return container;
  }

  @Input
  @Optional
  boolean getUseOnlyProjectCache() {
    return useOnlyProjectCache.get();
  }
}
