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

import com.google.cloud.tools.jib.gradle.skaffold.SkaffoldParameters;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
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
 *     image = 'gcr.io/my-gcp-project/my-base-image'
 *     credHelper = 'gcr'
 *     platforms {
 *       platform {
 *         os = 'linux'
 *         architecture = 'amd64'
 *       }
 *     }
 *   }
 *   to {
 *     image = 'gcr.io/gcp-project/my-app:built-with-jib'
 *     credHelper = 'ecr-login'
 *   }
 *   container {
 *     jvmFlags = ['-Xms512m', '-Xdebug']
 *     mainClass = 'com.mycompany.myproject.Main'
 *     args = ['arg1', 'arg2']
 *     ports = ['1000', '2000-2010', '3000']
 *     format = OCI
 *     appRoot = '/app'
 *   }
 *   extraDirectories {
 *     paths = ['/path/to/extra/dir', 'can/be/relative/to/project/root']
 *     permissions = [
 *       '/path/on/container/file1': 744,
 *       '/path/on/container/file2': 123
 *     ]
 *   }
 *   outputPaths {
 *     tar = file('reative/to/project/root/jib-image.tar')
 *     digest = file('/absolute/path/jib-image.digest')
 *     imageId = file("$buildDir/jib-image.id")
 *   }
 *   allowInsecureRegistries = false
 *   containerizingMode = 'exploded'
 *   pluginExtensions {
 *     pluginExtension {
 *       implementation = 'com.example.ThirdPartyJibGradleExtension'
 *       properties = [customKey: 'value]
 *     }
 *   }
 * }
 * }</pre>
 */
public class JibExtension {

  // Defines default configuration values.
  private static final boolean DEFAULT_ALLOW_INSECURE_REGISTIRIES = false;
  private static final String DEFAULT_CONTAINERIZING_MODE = "exploded";

  private final BaseImageParameters from;
  private final TargetImageParameters to;
  private final ContainerParameters container;
  private final ExtraDirectoriesParameters extraDirectories;
  private final DockerClientParameters dockerClient;
  private final OutputPathsParameters outputPaths;
  private final SkaffoldParameters skaffold;
  private final Property<Boolean> allowInsecureRegistries;
  private final Property<String> containerizingMode;
  private final Property<String> configurationName;
  private final ListProperty<ExtensionParameters> pluginExtensions;
  private final ExtensionParametersSpec extensionParametersSpec;

  /**
   * Should be called using {@link org.gradle.api.plugins.ExtensionContainer#create}.
   *
   * @param project the injected gradle project
   */
  public JibExtension(Project project) {
    ObjectFactory objectFactory = project.getObjects();

    from = objectFactory.newInstance(BaseImageParameters.class);
    to = objectFactory.newInstance(TargetImageParameters.class);
    container = objectFactory.newInstance(ContainerParameters.class);
    extraDirectories = objectFactory.newInstance(ExtraDirectoriesParameters.class, project);
    dockerClient = objectFactory.newInstance(DockerClientParameters.class);
    outputPaths = objectFactory.newInstance(OutputPathsParameters.class, project);
    skaffold = objectFactory.newInstance(SkaffoldParameters.class, project);

    pluginExtensions = objectFactory.listProperty(ExtensionParameters.class).empty();
    extensionParametersSpec =
        objectFactory.newInstance(ExtensionParametersSpec.class, pluginExtensions);
    allowInsecureRegistries = objectFactory.property(Boolean.class);
    containerizingMode = objectFactory.property(String.class);
    configurationName =
        objectFactory
            .property(String.class)
            .convention(
                project.provider(
                    () ->
                        System.getProperty(
                            PropertyNames.CONFIGURATION_NAME,
                            JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)));

    // Sets defaults.
    allowInsecureRegistries.set(DEFAULT_ALLOW_INSECURE_REGISTIRIES);
    containerizingMode.set(DEFAULT_CONTAINERIZING_MODE);
  }

  public void from(Action<? super BaseImageParameters> action) {
    action.execute(from);
  }

  public void to(Action<? super TargetImageParameters> action) {
    action.execute(to);
  }

  public void container(Action<? super ContainerParameters> action) {
    action.execute(container);
  }

  public void extraDirectories(Action<? super ExtraDirectoriesParameters> action) {
    action.execute(extraDirectories);
  }

  public void dockerClient(Action<? super DockerClientParameters> action) {
    action.execute(dockerClient);
  }

  public void outputPaths(Action<? super OutputPathsParameters> action) {
    action.execute(outputPaths);
  }

  public void skaffold(Action<? super SkaffoldParameters> action) {
    action.execute(skaffold);
  }

  public void pluginExtensions(Action<? super ExtensionParametersSpec> action) {
    action.execute(extensionParametersSpec);
  }

  public void setAllowInsecureRegistries(boolean allowInsecureRegistries) {
    this.allowInsecureRegistries.set(allowInsecureRegistries);
  }

  public void setContainerizingMode(String containerizingMode) {
    this.containerizingMode.set(containerizingMode);
  }

  @Nested
  @Optional
  public BaseImageParameters getFrom() {
    return from;
  }

  @Nested
  @Optional
  public TargetImageParameters getTo() {
    return to;
  }

  @Nested
  @Optional
  public ContainerParameters getContainer() {
    return container;
  }

  @Nested
  @Optional
  public ExtraDirectoriesParameters getExtraDirectories() {
    return extraDirectories;
  }

  @Nested
  @Optional
  public DockerClientParameters getDockerClient() {
    return dockerClient;
  }

  @Nested
  @Optional
  public OutputPathsParameters getOutputPaths() {
    return outputPaths;
  }

  @Nested
  @Optional
  public SkaffoldParameters getSkaffold() {
    return skaffold;
  }

  @Input
  boolean getAllowInsecureRegistries() {
    if (System.getProperty(PropertyNames.ALLOW_INSECURE_REGISTRIES) != null) {
      return Boolean.getBoolean(PropertyNames.ALLOW_INSECURE_REGISTRIES);
    }
    return allowInsecureRegistries.get();
  }

  @Input
  @Optional
  public String getContainerizingMode() {
    String property = System.getProperty(PropertyNames.CONTAINERIZING_MODE);
    return property != null ? property : containerizingMode.get();
  }

  @Input
  @Optional
  public Property<String> getConfigurationName() {
    return configurationName;
  }

  @Nested
  @Optional
  public ListProperty<ExtensionParameters> getPluginExtensions() {
    return pluginExtensions;
  }
}
