/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.cli;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.Ports;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.cli.jar.JarFiles;
import com.google.cloud.tools.jib.cli.jar.JarProcessor;
import com.google.cloud.tools.jib.cli.jar.JarProcessors;
import com.google.cloud.tools.jib.cli.jar.ProcessingMode;
import com.google.cloud.tools.jib.cli.logging.CliLogger;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.SingleThreadedExecutor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

@CommandLine.Command(
    name = "jar",
    showAtFileInUsageHelp = true,
    description = "Containerize a jar",
    hidden = true)
public class Jar implements Callable<Integer> {

  @CommandLine.Spec
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private CommandSpec spec;

  @CommandLine.Mixin
  @VisibleForTesting
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  CommonCliOptions commonCliOptions;

  @CommandLine.Parameters(description = "The path to the jar file (ex: path/to/my-jar.jar)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private Path jarFile;

  @CommandLine.Option(
      names = "--mode",
      defaultValue = "exploded",
      paramLabel = "<mode>",
      description =
          "The jar processing mode, candidates: ${COMPLETION-CANDIDATES}, default: ${DEFAULT-VALUE}")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private ProcessingMode mode;

  @CommandLine.Option(
      names = "--from",
      paramLabel = "<from>",
      description = "The base image to use.")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private String from;

  @CommandLine.Option(
      names = "--jvm-flags",
      paramLabel = "<jvm-flags>",
      split = ",",
      description = "JVM arguments")
  private List<String> jvmFlags = Collections.emptyList();

  @CommandLine.Option(
      names = "--expose",
      paramLabel = "<exposed-ports>",
      split = ",",
      description = "Ports to expose on container")
  private List<String> exposedPorts = Collections.emptyList();

  @CommandLine.Option(
      names = "--volumes",
      paramLabel = "<volumes>",
      split = ",",
      description = "Directories on container to hold extra volumes")
  private List<String> volumes = Collections.emptyList();

  @CommandLine.Option(
      names = "--environment-variables",
      paramLabel = "<environment-variables>",
      split = ",",
      description =
          "Environment Variables to write into container. Usage example: --environment-variables key1=value1,key2=value2")
  private Map<String, String> environment = new LinkedHashMap<>();

  @CommandLine.Option(
      names = "--labels",
      paramLabel = "<labels>",
      split = ",",
      description =
          "Labels to write into container metadata. Usage example: --labels label1=value1,label2=value2")
  private Map<String, String> labels = new LinkedHashMap<>();

  @Override
  public Integer call() {
    try {
      // Temporarily disable the command, but allow to proceed in tests.
      Class.forName("org.junit.Test");
    } catch (ClassNotFoundException ex) {
      throw new UnsupportedOperationException("jar command not implemented");
    }

    commonCliOptions.validate();
    SingleThreadedExecutor executor = new SingleThreadedExecutor();
    try (TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider()) {

      ConsoleLogger logger =
          CliLogger.newLogger(
              commonCliOptions.getVerbosity(),
              commonCliOptions.getConsoleOutput(),
              spec.commandLine().getOut(),
              spec.commandLine().getErr(),
              executor);

      if (!Files.exists(jarFile)) {
        logger.log(LogEvent.Level.ERROR, "The file path provided does not exist: " + jarFile);
        return 1;
      }
      if (Files.isDirectory(jarFile)) {
        logger.log(
            LogEvent.Level.ERROR,
            "The file path provided is for a directory. Please provide a path to a JAR: "
                + jarFile);
        return 1;
      }

      JarProcessor processor = JarProcessors.from(jarFile, tempDirectoryProvider, mode);
      JibContainerBuilder containerBuilder =
          JarFiles.toJibContainerBuilder(processor, this, commonCliOptions, logger);
      CacheDirectories cacheDirectories =
          CacheDirectories.from(commonCliOptions, jarFile.toAbsolutePath().getParent());
      Containerizer containerizer = Containerizers.from(commonCliOptions, logger, cacheDirectories);
      containerBuilder.containerize(containerizer);
    } catch (Exception ex) {
      if (commonCliOptions.isStacktrace()) {
        ex.printStackTrace();
      }
      System.err.println(ex.getClass().getName() + ": " + ex.getMessage());
      return 1;
    } finally {
      executor.shutDownAndAwaitTermination(Duration.ofSeconds(3));
    }
    return 0;
  }

  /**
   * Returns the user-specified base image.
   *
   * @return an optional base image
   */
  public Optional<String> getFrom() {
    if (from != null) {
      return Optional.of(from);
    }
    return Optional.empty();
  }

  public List<String> getJvmFlags() {
    return jvmFlags;
  }

  /**
   * Returns set of {@link Port} representing ports to be exposed on container (if specified).
   *
   * @return set of exposed ports
   */
  public Set<Port> getExposedPorts() {
    return (exposedPorts == null) ? ImmutableSet.of() : Ports.parse(exposedPorts);
  }

  /**
   * Returns a set of {@link AbsoluteUnixPath} representing directories on container to hold volumes
   * (if specified).
   *
   * @return set of volumes
   */
  public Set<AbsoluteUnixPath> getVolumes() {
    if (volumes == null) {
      return ImmutableSet.of();
    }
    return volumes.stream().map(AbsoluteUnixPath::get).collect(Collectors.toSet());
  }

  public Map<String, String> getEnvironment() {
    return environment;
  }

  public Map<String, String> getLabels() {
    return labels;
  }
}
