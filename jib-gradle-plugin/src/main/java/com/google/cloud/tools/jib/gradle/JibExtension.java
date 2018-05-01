/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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

import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.OCIManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
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
 *   reproducible = true
 *   format = OCI
 * }
 * }</pre>
 */
public class JibExtension {

  // TODO: Consolidate with BuildImageMojo#ImageFormat.
  /** Enumeration of {@link BuildableManifestTemplate}s. */
  @VisibleForTesting
  enum ImageFormat {
    Docker(V22ManifestTemplate.class),
    OCI(OCIManifestTemplate.class);

    private final Class<? extends BuildableManifestTemplate> manifestTemplateClass;

    ImageFormat(Class<? extends BuildableManifestTemplate> manifestTemplateClass) {
      this.manifestTemplateClass = manifestTemplateClass;
    }

    private Class<? extends BuildableManifestTemplate> getManifestTemplateClass() {
      return manifestTemplateClass;
    }
  }

  // Defines default configuration values.
  private static final String DEFAULT_FROM_IMAGE = "gcr.io/distroless/java";
  private static final List<String> DEFAULT_JVM_FLAGS = Collections.emptyList();
  private static final ImageFormat DEFAULT_FORMAT = ImageFormat.Docker;
  private static final boolean DEFAULT_REPRODUCIBLE = true;
  private static final boolean DEFAULT_USE_ONLY_PROJECT_CACHE = false;

  private ImageConfiguration from;
  private ImageConfiguration to;
  private final ListProperty<String> jvmFlags;
  private final Property<String> mainClass;
  private final Property<Boolean> reproducible;
  private final Property<ImageFormat> format;
  private final Property<Boolean> useOnlyProjectCache;

  public JibExtension(Project project) {
    ObjectFactory objectFactory = project.getObjects();

    from = objectFactory.newInstance(ImageConfiguration.class);
    to = objectFactory.newInstance(ImageConfiguration.class);

    jvmFlags = objectFactory.listProperty(String.class);
    mainClass = objectFactory.property(String.class);
    reproducible = objectFactory.property(Boolean.class);
    format = objectFactory.property(ImageFormat.class);
    useOnlyProjectCache = objectFactory.property(Boolean.class);

    // Sets defaults.
    from.setImage(DEFAULT_FROM_IMAGE);
    jvmFlags.set(DEFAULT_JVM_FLAGS);
    reproducible.set(DEFAULT_REPRODUCIBLE);
    format.set(DEFAULT_FORMAT);
    useOnlyProjectCache.set(DEFAULT_USE_ONLY_PROJECT_CACHE);
  }

  public void from(Action<? super ImageConfiguration> action) {
    action.execute(from);
  }

  public void to(Action<? super ImageConfiguration> action) {
    action.execute(to);
  }

  public void setJvmFlags(List<String> jvmFlags) {
    this.jvmFlags.set(jvmFlags);
  }

  public void setMainClass(String mainClass) {
    this.mainClass.set(mainClass);
  }

  public void setReproducible(boolean isEnabled) {
    reproducible.set(isEnabled);
  }

  public void setFormat(ImageFormat format) {
    this.format.set(format);
  }

  public void setUseOnlyProjectCache(boolean useOnlyProjectCache) {
    this.useOnlyProjectCache.set(useOnlyProjectCache);
  }

  @Nested
  @Optional
  ImageConfiguration getFrom() {
    return from;
  }

  @Nested
  ImageConfiguration getTo() {
    return to;
  }

  @Input
  List<String> getJvmFlags() {
    return jvmFlags.get();
  }

  @Input
  @Nullable
  @Optional
  String getMainClass() {
    return mainClass.getOrNull();
  }

  @Input
  @Optional
  boolean getReproducible() {
    return reproducible.get();
  }

  @Input
  @Optional
  Class<? extends BuildableManifestTemplate> getFormat() {
    return format.get().getManifestTemplateClass();
  }

  @Input
  @Optional
  boolean getUseOnlyProjectCache() {
    return useOnlyProjectCache.get();
  }
}
