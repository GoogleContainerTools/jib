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

import com.google.common.annotations.VisibleForTesting;
import groovy.lang.Closure;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskValidationException;
import org.gradle.util.ConfigureUtil;

/**
 * Builds a container image.
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
public class BuildImageTask extends DefaultTask {

  /** Enumeration of supported image formats. */
  private enum ImageFormat {
    DOCKER,
    OCI
  }

  /**
   * Configures an image to be used in the build steps. This is configurable with Groovy closures.
   */
  @VisibleForTesting
  class ImageConfiguration {

    @Nullable private String image;
    @Nullable private String credHelper;

    @VisibleForTesting
    @Nullable
    String getImage() {
      return image;
    }

    @VisibleForTesting
    @Nullable
    String getCredHelper() {
      return credHelper;
    }

    /**
     * @param closureName the name of the method the closure was passed to
     * @param closure the closure to apply
     */
    private ImageConfiguration configure(String closureName, Closure closure) {
      ConfigureUtil.configureSelf(closure, this);

      // 'image' is a required property
      if (image == null) {
        // The wrapping mimics Gradle's built-in task configuration validation.
        throw new TaskValidationException(
            "A problem was found with the configuration of task '" + getName() + "'",
            Collections.singletonList(
                new InvalidUserDataException(
                    "'" + closureName + "' closure must define 'image' property")));
      }
      return this;
    }
  }

  @Nullable private ImageConfiguration from;
  @Nullable private ImageConfiguration to;

  private List<String> jvmFlags = new ArrayList<>();
  @Nullable private String mainClass;
  private boolean reproducible = true;
  private ImageFormat format = ImageFormat.DOCKER;

  /** Configures the base image. */
  public void from(Closure<?> closure) {
    from = new ImageConfiguration().configure("from", closure);
  }

  /** Configures the target image. */
  public void to(Closure<?> closure) {
    to = new ImageConfiguration().configure("to", closure);
  }

  @Nullable
  @Input
  @Optional
  public ImageConfiguration getFrom() {
    return from;
  }

  @Nullable
  @Input
  public ImageConfiguration getTo() {
    return to;
  }

  @Input
  public List<String> getJvmFlags() {
    return jvmFlags;
  }

  public void setJvmFlags(List<String> jvmFlags) {
    this.jvmFlags = jvmFlags;
  }

  @Input
  @Optional
  @Nullable
  public String getMainClass() {
    return mainClass;
  }

  public void setMainClass(@Nullable String mainClass) {
    this.mainClass = mainClass;
  }

  @Input
  public boolean getReproducible() {
    return reproducible;
  }

  public void setReproducible(boolean isEnabled) {
    reproducible = isEnabled;
  }

  @Input
  public ImageFormat getFormat() {
    return format;
  }

  public void setFormat(ImageFormat format) {
    this.format = format;
  }

  @TaskAction
  public void buildImage() {
    // TODO: Implement.
  }
}
