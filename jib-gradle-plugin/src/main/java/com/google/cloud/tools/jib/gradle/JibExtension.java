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
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

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

  /**
   * Configures an image to be used in the build steps. This is configurable with Groovy closures.
   */
  public static class ImageConfiguration {

    @Nullable private String image;
    @Nullable private String credHelper;

    @Nullable
    public String getImage() {
      return image;
    }

    public void setImage(String image) {
      this.image = image;
    }

    @Nullable
    public String getCredHelper() {
      return credHelper;
    }

    public void setCredHelper(String credHelper) {
      this.credHelper = credHelper;
    }
  }

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

  private final ImageConfiguration from;
  private final ImageConfiguration to;
  private final ListProperty<String> jvmFlags;
  private final Property<String> mainClass;
  private final Property<Boolean> reproducible;
  private Property<ImageFormat> format;

  @Inject
  public JibExtension(Project project) {
    ObjectFactory objectFactory = project.getObjects();

    from = objectFactory.newInstance(ImageConfiguration.class);
    to = objectFactory.newInstance(ImageConfiguration.class);

    jvmFlags = objectFactory.listProperty(String.class);
    mainClass = objectFactory.property(String.class);
    reproducible = objectFactory.property(Boolean.class);
    format = objectFactory.property(ImageFormat.class);

    // Sets defaults.
    from.setImage(DEFAULT_FROM_IMAGE);
    jvmFlags.set(DEFAULT_JVM_FLAGS);
    reproducible.set(DEFAULT_REPRODUCIBLE);
    format.set(DEFAULT_FORMAT);
  }

  public void from(Action<? super ImageConfiguration> action) {
    action.execute(from);
  }

  public void to(Action<? super ImageConfiguration> action) {
    action.execute(to);
  }

  List<String> getJvmFlags() {
    return jvmFlags.get();
  }

  public void setJvmFlags(List<String> jvmFlags) {
    this.jvmFlags.set(jvmFlags);
  }

  @Nullable
  String getMainClass() {
    return mainClass.getOrNull();
  }

  public void setMainClass(String mainClass) {
    this.mainClass.set(mainClass);
  }

  boolean getReproducible() {
    return reproducible.get();
  }

  public void setReproducible(boolean isEnabled) {
    reproducible.set(isEnabled);
  }

  Class<? extends BuildableManifestTemplate> getFormat() {
    return format.get().getManifestTemplateClass();
  }

  public void setFormat(ImageFormat format) {
    this.format.set(format);
  }

  ImageConfiguration getFrom() {
    return from;
  }

  ImageConfiguration getTo() {
    return to;
  }
}
