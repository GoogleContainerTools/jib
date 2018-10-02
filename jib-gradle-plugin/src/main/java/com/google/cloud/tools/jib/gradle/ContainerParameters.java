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
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
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
  private String appRoot = "";

  @Input
  @Optional
  public boolean getUseCurrentTimestamp() {
    if (System.getProperty(PropertyNames.containerUseCurrentTimestamp) != null) {
      return Boolean.getBoolean(PropertyNames.containerUseCurrentTimestamp);
    }
    return useCurrentTimestamp;
  }

  public void setUseCurrentTimestamp(boolean useCurrentTimestamp) {
    this.useCurrentTimestamp = useCurrentTimestamp;
  }

  @Input
  @Optional
  public List<String> getEntrypoint() {
    if (System.getProperty(PropertyNames.containerEntrypoint) != null) {
      return ConfigurationPropertyValidator.parseListProperty(
          System.getProperty(PropertyNames.containerEntrypoint));
    }
    return entrypoint;
  }

  public void setEntrypoint(List<String> entrypoint) {
    this.entrypoint = entrypoint;
  }

  @Input
  @Optional
  public List<String> getJvmFlags() {
    if (System.getProperty(PropertyNames.containerJvmFlags) != null) {
      return ConfigurationPropertyValidator.parseListProperty(
          System.getProperty(PropertyNames.containerJvmFlags));
    }
    return jvmFlags;
  }

  public void setJvmFlags(List<String> jvmFlags) {
    this.jvmFlags = jvmFlags;
  }

  @Input
  @Optional
  public Map<String, String> getEnvironment() {
    if (System.getProperty(PropertyNames.containerEnvironment) != null) {
      return ConfigurationPropertyValidator.parseMapProperty(
          System.getProperty(PropertyNames.containerEnvironment));
    }
    return environment;
  }

  public void setEnvironment(Map<String, String> environment) {
    this.environment = environment;
  }

  @Input
  @Nullable
  @Optional
  public String getMainClass() {
    if (System.getProperty(PropertyNames.containerMainClass) != null) {
      return System.getProperty(PropertyNames.containerMainClass);
    }
    return mainClass;
  }

  public void setMainClass(String mainClass) {
    this.mainClass = mainClass;
  }

  @Input
  @Optional
  public List<String> getArgs() {
    if (System.getProperty(PropertyNames.containerArgs) != null) {
      return ConfigurationPropertyValidator.parseListProperty(
          System.getProperty(PropertyNames.containerArgs));
    }
    return args;
  }

  public void setArgs(List<String> args) {
    this.args = args;
  }

  @Input
  @Optional
  public Class<? extends BuildableManifestTemplate> getFormat() {
    if (System.getProperty(PropertyNames.containerFormat) != null) {
      return ImageFormat.valueOf(System.getProperty(PropertyNames.containerFormat))
          .getManifestTemplateClass();
    }
    return Preconditions.checkNotNull(format).getManifestTemplateClass();
  }

  public void setFormat(ImageFormat format) {
    this.format = format;
  }

  @Input
  @Optional
  public List<String> getPorts() {
    if (System.getProperty(PropertyNames.containerPorts) != null) {
      return ConfigurationPropertyValidator.parseListProperty(
          System.getProperty(PropertyNames.containerPorts));
    }
    return ports;
  }

  public void setPorts(List<String> ports) {
    this.ports = ports;
  }

  @Input
  @Optional
  public Map<String, String> getLabels() {
    if (System.getProperty(PropertyNames.containerLabels) != null) {
      return ConfigurationPropertyValidator.parseMapProperty(
          System.getProperty(PropertyNames.containerLabels));
    }
    return labels;
  }

  public void setLabels(Map<String, String> labels) {
    this.labels = labels;
  }

  @Input
  @Optional
  public String getAppRoot() {
    if (System.getProperty(PropertyNames.containerAppRoot) != null) {
      return System.getProperty(PropertyNames.containerAppRoot);
    }
    return appRoot;
  }

  public void setAppRoot(String appRoot) {
    this.appRoot = appRoot;
  }
}
