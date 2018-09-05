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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.image.ImageFormat;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * A bean that configures properties of the container run from the image. This is configurable with
 * Groovy closures and can be validated when used as a task input.
 */
public class ContainerParameters {

  private boolean useCurrentTimestamp = false;
  private List<String> jvmFlags = Collections.emptyList();
  private Map<String, String> environment = Collections.emptyMap();
  private List<String> entrypoint = Collections.emptyList();
  @Nullable private String mainClass;
  private List<String> args = Collections.emptyList();
  private ImageFormat format = ImageFormat.Docker;
  private List<String> ports = Collections.emptyList();
  private Map<String, String> labels = Collections.emptyMap();

  @Input
  @Optional
  public boolean getUseCurrentTimestamp() {
    return useCurrentTimestamp;
  }

  public void setUseCurrentTimestamp(boolean useCurrentTimestamp) {
    this.useCurrentTimestamp = useCurrentTimestamp;
  }

  @Input
  @Optional
  public List<String> getEntrypoint() {
    return entrypoint;
  }

  public void setEntrypoint(List<String> entrypoint) {
    this.entrypoint = entrypoint;
  }

  @Input
  @Optional
  public List<String> getJvmFlags() {
    return jvmFlags;
  }

  public void setJvmFlags(List<String> jvmFlags) {
    this.jvmFlags = jvmFlags;
  }

  @Input
  @Optional
  public Map<String, String> getEnvironment() {
    return environment;
  }

  public void setEnvironment(Map<String, String> environment) {
    this.environment = environment;
  }

  @Input
  @Nullable
  @Optional
  public String getMainClass() {
    return mainClass;
  }

  public void setMainClass(String mainClass) {
    this.mainClass = mainClass;
  }

  @Input
  @Optional
  public List<String> getArgs() {
    return args;
  }

  public void setArgs(List<String> args) {
    this.args = args;
  }

  @Input
  @Optional
  public Class<? extends BuildableManifestTemplate> getFormat() {
    return Preconditions.checkNotNull(format).getManifestTemplateClass();
  }

  public void setFormat(ImageFormat format) {
    this.format = format;
  }

  @Input
  @Optional
  public List<String> getPorts() {
    return ports;
  }

  public void setPorts(List<String> ports) {
    this.ports = ports;
  }

  @Input
  @Optional
  public Map<String, String> getLabels() {
    return labels;
  }

  public void setLabels(Map<String, String> labels) {
    this.labels = labels;
  }
}
