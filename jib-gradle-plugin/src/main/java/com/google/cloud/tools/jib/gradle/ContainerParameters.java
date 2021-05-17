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

import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * A bean that configures properties of the container run from the image. This is configurable with
 * Groovy closures and can be validated when used as a task input.
 */
public class ContainerParameters {

  private List<String> jvmFlags = Collections.emptyList();
  private Map<String, String> environment = Collections.emptyMap();
  @Nullable private List<String> entrypoint;
  private List<String> extraClasspath = Collections.emptyList();
  private boolean expandClasspathDependencies;
  @Nullable private String mainClass;
  @Nullable private List<String> args;
  private ImageFormat format = ImageFormat.Docker;
  private List<String> ports = Collections.emptyList();
  private List<String> volumes = Collections.emptyList();
  private MapProperty<String, String> labels;
  private String appRoot = "";
  @Nullable private String user;
  @Nullable private String workingDirectory;
  private String filesModificationTime = "EPOCH_PLUS_SECOND";
  private String creationTime = "EPOCH";

  @Inject
  public ContainerParameters(ObjectFactory objectFactory) {
    labels = objectFactory.mapProperty(String.class, String.class).empty();
  }

  @Input
  @Nullable
  @Optional
  public List<String> getEntrypoint() {
    if (System.getProperty(PropertyNames.CONTAINER_ENTRYPOINT) != null) {
      return ConfigurationPropertyValidator.parseListProperty(
          System.getProperty(PropertyNames.CONTAINER_ENTRYPOINT));
    }
    return entrypoint;
  }

  public void setEntrypoint(List<String> entrypoint) {
    this.entrypoint = entrypoint;
  }

  public void setEntrypoint(String entrypoint) {
    this.entrypoint = Collections.singletonList(entrypoint);
  }

  @Input
  @Optional
  public List<String> getJvmFlags() {
    if (System.getProperty(PropertyNames.CONTAINER_JVM_FLAGS) != null) {
      return ConfigurationPropertyValidator.parseListProperty(
          System.getProperty(PropertyNames.CONTAINER_JVM_FLAGS));
    }
    return jvmFlags;
  }

  public void setJvmFlags(List<String> jvmFlags) {
    this.jvmFlags = jvmFlags;
  }

  @Input
  @Optional
  public Map<String, String> getEnvironment() {
    if (System.getProperty(PropertyNames.CONTAINER_ENVIRONMENT) != null) {
      return ConfigurationPropertyValidator.parseMapProperty(
          System.getProperty(PropertyNames.CONTAINER_ENVIRONMENT));
    }
    return environment;
  }

  public void setEnvironment(Map<String, String> environment) {
    this.environment = environment;
  }

  @Input
  @Optional
  public List<String> getExtraClasspath() {
    if (System.getProperty(PropertyNames.CONTAINER_EXTRA_CLASSPATH) != null) {
      return ConfigurationPropertyValidator.parseListProperty(
          System.getProperty(PropertyNames.CONTAINER_EXTRA_CLASSPATH));
    }
    return extraClasspath;
  }

  public void setExtraClasspath(List<String> classpath) {
    extraClasspath = classpath;
  }

  @Input
  public boolean getExpandClasspathDependencies() {
    if (System.getProperty(PropertyNames.EXPAND_CLASSPATH_DEPENDENCIES) != null) {
      return Boolean.valueOf(System.getProperty(PropertyNames.EXPAND_CLASSPATH_DEPENDENCIES));
    }
    return expandClasspathDependencies;
  }

  public void setExpandClasspathDependencies(boolean expand) {
    expandClasspathDependencies = expand;
  }

  @Input
  @Nullable
  @Optional
  public String getMainClass() {
    if (System.getProperty(PropertyNames.CONTAINER_MAIN_CLASS) != null) {
      return System.getProperty(PropertyNames.CONTAINER_MAIN_CLASS);
    }
    return mainClass;
  }

  public void setMainClass(String mainClass) {
    this.mainClass = mainClass;
  }

  @Input
  @Nullable
  @Optional
  public List<String> getArgs() {
    if (System.getProperty(PropertyNames.CONTAINER_ARGS) != null) {
      return ConfigurationPropertyValidator.parseListProperty(
          System.getProperty(PropertyNames.CONTAINER_ARGS));
    }
    return args;
  }

  public void setArgs(List<String> args) {
    this.args = args;
  }

  @Input
  @Optional
  public ImageFormat getFormat() {
    if (System.getProperty(PropertyNames.CONTAINER_FORMAT) != null) {
      return ImageFormat.valueOf(System.getProperty(PropertyNames.CONTAINER_FORMAT));
    }
    return Preconditions.checkNotNull(format);
  }

  public void setFormat(ImageFormat format) {
    this.format = format;
  }

  @Input
  @Optional
  public List<String> getPorts() {
    if (System.getProperty(PropertyNames.CONTAINER_PORTS) != null) {
      return ConfigurationPropertyValidator.parseListProperty(
          System.getProperty(PropertyNames.CONTAINER_PORTS));
    }
    return ports;
  }

  public void setPorts(List<String> ports) {
    this.ports = ports;
  }

  @Input
  @Optional
  public List<String> getVolumes() {
    if (System.getProperty(PropertyNames.CONTAINER_VOLUMES) != null) {
      return ConfigurationPropertyValidator.parseListProperty(
          System.getProperty(PropertyNames.CONTAINER_VOLUMES));
    }
    return volumes;
  }

  public void setVolumes(List<String> volumes) {
    this.volumes = volumes;
  }

  @Input
  @Optional
  public MapProperty<String, String> getLabels() {
    String labelsProperty = System.getProperty(PropertyNames.CONTAINER_LABELS);
    if (labelsProperty != null) {
      Map<String, String> parsedLabels =
          ConfigurationPropertyValidator.parseMapProperty(labelsProperty);
      if (!parsedLabels.equals(labels.get())) {
        labels.set(parsedLabels);
      }
    }
    return labels;
  }

  @Input
  @Optional
  public String getAppRoot() {
    if (System.getProperty(PropertyNames.CONTAINER_APP_ROOT) != null) {
      return System.getProperty(PropertyNames.CONTAINER_APP_ROOT);
    }
    return appRoot;
  }

  public void setAppRoot(String appRoot) {
    this.appRoot = appRoot;
  }

  @Input
  @Nullable
  @Optional
  public String getUser() {
    if (System.getProperty(PropertyNames.CONTAINER_USER) != null) {
      return System.getProperty(PropertyNames.CONTAINER_USER);
    }
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  @Input
  @Nullable
  @Optional
  public String getWorkingDirectory() {
    if (System.getProperty(PropertyNames.CONTAINER_WORKING_DIRECTORY) != null) {
      return System.getProperty(PropertyNames.CONTAINER_WORKING_DIRECTORY);
    }
    return workingDirectory;
  }

  public void setWorkingDirectory(String workingDirectory) {
    this.workingDirectory = workingDirectory;
  }

  @Input
  @Optional
  public String getFilesModificationTime() {
    if (System.getProperty(PropertyNames.CONTAINER_FILES_MODIFICATION_TIME) != null) {
      return System.getProperty(PropertyNames.CONTAINER_FILES_MODIFICATION_TIME);
    }
    return filesModificationTime;
  }

  public void setFilesModificationTime(String filesModificationTime) {
    this.filesModificationTime = filesModificationTime;
  }

  @Input
  @Optional
  public String getCreationTime() {
    if (System.getProperty(PropertyNames.CONTAINER_CREATION_TIME) != null) {
      return System.getProperty(PropertyNames.CONTAINER_CREATION_TIME);
    }
    return creationTime;
  }

  public void setCreationTime(String creationTime) {
    this.creationTime = creationTime;
  }
}
