/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.jib.builder.configuration;

import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable configuration options for the builder process. */
public class BuildConfiguration {

  private static class Parameters {

    /**
     * The set of {@link ConfigurationParameter}s that constitute the configuration.
     *
     * <p>When adding new configuration parameters,
     *
     * <ol>
     *   <li>Create a new {@link ConfigurationParameter} subclass.
     *   <li>Add the new subclass to this set.
     *   <li>Add the appropriate setter to {@link Builder}.
     *   <li>Add the appropriate getter to {@link BuildConfiguration}.
     * </ol>
     */
    private static final ImmutableSet<Class<? extends ConfigurationParameter<?>>> PARAMETER_SET =
        ImmutableSet.of(
            BaseImageParameter.class,
            TargetImageParameter.class,
            CredentialHelpersParameter.class,
            KnownRegistryCredentialsParameter.class,
            EnableReproducibleBuildsParameter.class,
            MainClassParameter.class,
            JvmFlagsParameter.class,
            EnvironmentParameter.class);

    private final Map<Class<? extends ConfigurationParameter<?>>, ConfigurationParameter<?>>
        parameterMap = new HashMap<>();

    private Parameters() {
      try {
        for (Class<? extends ConfigurationParameter<?>> parameterClass : PARAMETER_SET) {
          parameterMap.put(parameterClass, parameterClass.newInstance());
        }

      } catch (IllegalAccessException | InstantiationException ex) {
        throw new IllegalStateException(ex);
      }
    }

    /** Sets the value for the parameter with class {@code parameterClass}. */
    private <T> void set(Class<? extends ConfigurationParameter<T>> parameterClass, T value) {
      get(parameterClass).set(value);
    }

    /**
     * Validates the parametes.
     *
     * @throws IllegalStateException if any parameter is invalid
     */
    private void validate() {
      ConfigurationParameterValidator configurationParameterValidator =
          new ConfigurationParameterValidator();
      for (Class<? extends ConfigurationParameter<?>> parameterClass : PARAMETER_SET) {
        configurationParameterValidator.validate(get(parameterClass));
      }
      if (configurationParameterValidator.hasError()) {
        throw new IllegalStateException(configurationParameterValidator.getErrorMessage());
      }
    }

    private <T extends ConfigurationParameter<?>> T get(Class<T> parameterClass) {
      return parameterClass.cast(parameterMap.get(parameterClass));
    }
  }

  public static class Builder {

    private final Parameters parameters = new Parameters();

    private BuildLogger buildLogger;

    private Builder(BuildLogger buildLogger) {
      this.buildLogger = buildLogger;
    }

    public Builder setBaseImage(ImageReference imageReference) {
      parameters.set(BaseImageParameter.class, imageReference);
      return this;
    }

    public Builder setTargetImage(ImageReference imageReference) {
      parameters.set(TargetImageParameter.class, imageReference);
      return this;
    }

    public Builder setCredentialHelperNames(List<String> credentialHelperNames) {
      parameters.set(CredentialHelpersParameter.class, credentialHelperNames);
      return this;
    }

    public Builder setKnownRegistryCredentials(RegistryCredentials knownRegistryCredentials) {
      parameters.set(KnownRegistryCredentialsParameter.class, knownRegistryCredentials);
      return this;
    }

    public Builder setEnableReproducibleBuilds(boolean isEnabled) {
      parameters.set(EnableReproducibleBuildsParameter.class, isEnabled);
      return this;
    }

    public Builder setMainClass(String mainClass) {
      parameters.set(MainClassParameter.class, mainClass);
      return this;
    }

    public Builder setJvmFlags(List<String> jvmFlags) {
      parameters.set(JvmFlagsParameter.class, jvmFlags);
      return this;
    }

    public Builder setEnvironment(Map<String, String> environmentMap) {
      parameters.set(EnvironmentParameter.class, environmentMap);
      return this;
    }

    /** @return the corresponding build configuration */
    public BuildConfiguration build() {
      parameters.validate();
      return new BuildConfiguration(buildLogger, parameters);
    }
  }

  private final BuildLogger buildLogger;
  private final Parameters parameters;

  public static Builder builder(BuildLogger buildLogger) {
    return new Builder(buildLogger);
  }

  private BuildConfiguration(BuildLogger buildLogger, Parameters parameters) {
    this.buildLogger = buildLogger;
    this.parameters = parameters;
  }

  public BuildLogger getBuildLogger() {
    return buildLogger;
  }

  public String getBaseImageRegistry() {
    return parameters.get(BaseImageParameter.class).get().getRegistry();
  }

  public String getBaseImageRepository() {
    return parameters.get(BaseImageParameter.class).get().getRepository();
  }

  public String getBaseImageTag() {
    return parameters.get(BaseImageParameter.class).get().getTag();
  }

  public String getTargetRegistry() {
    return parameters.get(TargetImageParameter.class).get().getRegistry();
  }

  public String getTargetRepository() {
    return parameters.get(TargetImageParameter.class).get().getRepository();
  }

  public String getTargetTag() {
    return parameters.get(TargetImageParameter.class).get().getTag();
  }

  public RegistryCredentials getKnownRegistryCredentials() {
    return parameters.get(KnownRegistryCredentialsParameter.class).get();
  }

  public List<String> getCredentialHelperNames() {
    return parameters.get(CredentialHelpersParameter.class).get();
  }

  public boolean getEnableReproducibleBuilds() {
    return parameters.get(EnableReproducibleBuildsParameter.class).get();
  }

  public String getMainClass() {
    return parameters.get(MainClassParameter.class).get();
  }

  public List<String> getJvmFlags() {
    return parameters.get(JvmFlagsParameter.class).get();
  }

  public Map<String, String> getEnvironment() {
    return parameters.get(EnvironmentParameter.class).get();
  }
}
