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
import org.gradle.util.Configurable;
import org.gradle.util.ConfigureUtil;

public class BuildImageTask extends DefaultTask {

  @VisibleForTesting
  class ImageConfiguration implements Configurable<Void> {

    private final String closureName;

    @Nullable private String image;
    @Nullable private String credHelper;

    private ImageConfiguration(String closureName) {
      this.closureName = closureName;
    }

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

    @Override
    public Void configure(Closure closure) {
      ConfigureUtil.configureSelf(closure, this);
      if (image == null) {
        throw new TaskValidationException(
            "A problem was found with the configuration of task '" + getName() + "'",
            Collections.singletonList(
                new InvalidUserDataException(
                    "'" + closureName + "' closure must define 'image' property")));
      }
      return null;
    }
  }

  private final ImageConfiguration from = new ImageConfiguration("from");
  private final ImageConfiguration to = new ImageConfiguration("to");

  private List<String> jvmFlags = new ArrayList<>();
  @Nullable private String mainClass;
  private boolean reproducible = true;
  private ImageFormat format = ImageFormat.DOCKER;

  public void from(Closure<?> closure) {
    from.configure(closure);
  }

  public void to(Closure<?> closure) {
    to.configure(closure);
  }

  @VisibleForTesting
  ImageConfiguration getFrom() {
    return from;
  }

  @VisibleForTesting
  ImageConfiguration getTo() {
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
