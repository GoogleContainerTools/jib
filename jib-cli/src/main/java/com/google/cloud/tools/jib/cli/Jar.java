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
import com.google.cloud.tools.jib.api.JibContainer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.cli.jar.JarFiles;
import com.google.cloud.tools.jib.cli.jar.ProcessingMode;
import com.google.cloud.tools.jib.cli.logging.CliLogger;
import com.google.cloud.tools.jib.plugins.common.globalconfig.GlobalConfig;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.SingleThreadedExecutor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.Futures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

@CommandLine.Command(
    name = "jar",
    mixinStandardHelpOptions = true,
    showAtFileInUsageHelp = true,
    description = "Containerize a jar")
public class Jar implements Callable<Integer> {

  @CommandLine.Spec
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private CommandSpec spec;

  @CommandLine.Mixin
  @VisibleForTesting
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  CommonCliOptions commonCliOptions;

  @CommandLine.Mixin
  @VisibleForTesting
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  CommonArtifactCommandOptions commonArtifactCommandOptions;

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
      names = "--jvm-flags",
      paramLabel = "<jvm-flag>",
      split = ",",
      description = "JVM arguments, example: --jvm-flags=-Dmy.property=value,-Xshare:off")
  private List<String> jvmFlags = Collections.emptyList();

  @Override
  public Integer call() {
    commonCliOptions.validate();
    SingleThreadedExecutor executor = new SingleThreadedExecutor();
    ConsoleLogger logger =
        CliLogger.newLogger(
            commonCliOptions.getVerbosity(),
            commonCliOptions.getHttpTrace(),
            commonCliOptions.getConsoleOutput(),
            spec.commandLine().getOut(),
            spec.commandLine().getErr(),
            executor);
    Future<Optional<String>> updateCheckFuture = Futures.immediateFuture(Optional.empty());
    try {
      JibCli.configureHttpLogging(commonCliOptions.getHttpTrace().toJulLevel());
      GlobalConfig globalConfig = GlobalConfig.readConfig();
      updateCheckFuture =
          JibCli.newUpdateChecker(
              globalConfig,
              commonCliOptions.getVerbosity(),
              logEvent -> logger.log(logEvent.getLevel(), logEvent.getMessage()));
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
      if (!commonArtifactCommandOptions.getEntrypoint().isEmpty() && !jvmFlags.isEmpty()) {
        logger.log(LogEvent.Level.WARN, "--jvm-flags is ignored when --entrypoint is specified");
      }

      CacheDirectories cacheDirectories =
          CacheDirectories.from(commonCliOptions, jarFile.toAbsolutePath().getParent());
      ArtifactProcessor processor =
          ArtifactProcessors.fromJar(jarFile, cacheDirectories, this, commonArtifactCommandOptions);
      JibContainerBuilder containerBuilder =
          JarFiles.toJibContainerBuilder(
              processor, this, commonCliOptions, commonArtifactCommandOptions, logger);
      Containerizer containerizer = Containerizers.from(commonCliOptions, logger, cacheDirectories);

      // Enable registry mirrors
      Multimaps.asMap(globalConfig.getRegistryMirrors()).forEach(containerizer::addRegistryMirrors);

      JibContainer jibContainer = containerBuilder.containerize(containerizer);
      JibCli.writeImageJson(commonCliOptions.getImageJsonPath(), jibContainer);
    } catch (Exception ex) {
      JibCli.logTerminatingException(logger, ex, commonCliOptions.isStacktrace());
      return 1;
    } finally {
      JibCli.finishUpdateChecker(logger, updateCheckFuture);
      executor.shutDownAndAwaitTermination(Duration.ofSeconds(3));
    }
    return 0;
  }

  public List<String> getJvmFlags() {
    return jvmFlags;
  }

  public ProcessingMode getMode() {
    return mode;
  }
}
