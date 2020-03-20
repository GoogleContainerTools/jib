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
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.cli.JibCli.ImageReferenceParser;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** A Jib CLI subcommand for building a container image from a set of files on disk. */
@Command(name = "build", description = "Create a container image from static files")
public class Build extends Building implements Callable<Integer> {

  @Parameters(
      index = "0",
      paramLabel = "base-image",
      description = "The base image (ex: busybox, nginx, gcr.io/distroless/java)",
      converter = ImageReferenceParser.class)
  @VisibleForTesting
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  ImageReference baseImage;

  @Parameters(
      index = "1",
      paramLabel = "destination-image",
      description =
          "The destination image (ex: localhost:5000/image:1.0, gcr.io/project/image:latest)",
      converter = ImageReferenceParser.class)
  @VisibleForTesting
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  ImageReference destinationImage;

  @Parameters(
      index = "2..*",
      paramLabel = "layer-spec",
      description =
          "Create a layer from local file-system. A layer-spec "
              + "is a set of mappings of the form:\n"
              + "    local/path[,/container/path[,directive1,...]]\n"
              + "Container path defaults to '/' if omitted.\n"
              + "Directives include:\n"
              + "\n"
              + "  name=<xxx>    set the layer name\n"
              + "  p=perms       set file and directory permissions:\n"
              + "    actual      use actual values in file-system\n"
              + "    <fff>:<ddd> octal file and directory permissions\n"
              + "  ts=timestamp  set last-modified timestamps:\n"
              + "    actual      use actual values in file-system\n"
              + "    <number>    seconds since Unix epoch\n"
              + "    <xxx>       date-time in ISO8601 format\n"
              + "\n"
              + "Default permissions are 644 for files and 755 for directories. "
              + "Default timestamps are 1970-01-01 00:00:01 UTC. "
              + "Multiple mappings may be specified, separated by a semi-colon ';'.",
      converter = LayerDefinitionParser.class)
  @VisibleForTesting
  @Nullable
  List<FileEntriesLayer> layers;

  @Override
  public Integer call() throws Exception {
    Consumer<LogEvent> logger = System.out::println;
    JibContainerBuilder builder = Jib.from(parent.toCredentialedImage(baseImage, logger));
    verbose("FROM " + baseImage);
    if (creationTime != null) {
      builder.setCreationTime(creationTime);
    }
    if (entrypoint != null) {
      verbose("ENTRYPOINT [" + Joiner.on(",").join(entrypoint) + "]");
      builder.setEntrypoint(entrypoint);
    }
    if (arguments != null) {
      verbose("CMD [" + Joiner.on(",").join(arguments) + "]");
      builder.setProgramArguments(arguments);
    }
    if (environment != null) {
      for (Entry<String, String> pair : environment.entrySet()) {
        verbose("ENV " + pair.getKey() + "=" + pair.getValue());
        builder.addEnvironmentVariable(pair.getKey(), pair.getValue());
      }
    }
    if (labels != null) {
      for (Entry<String, String> pair : labels.entrySet()) {
        verbose("LABEL " + pair.getKey() + "=" + pair.getValue());
        builder.addLabel(pair.getKey(), pair.getValue());
      }
    }
    if (ports != null) {
      for (Port port : ports) {
        verbose("EXPOSE " + port);
        builder.addExposedPort(port);
      }
    }
    if (volumes != null) {
      for (AbsoluteUnixPath volume : volumes) {
        verbose("VOLUME " + volume);
        builder.addVolume(volume);
      }
    }
    if (user != null) {
      verbose("USER " + user);
      builder.setUser(user);
    }
    if (layers != null) {
      for (FileEntriesLayer layer : layers) {
        builder.addFileEntriesLayer(layer);
      }
    }
    Containerizer containerizer =
        pushMode.toDocker
            ? Containerizer.to(DockerDaemonImage.named(destinationImage))
            : Containerizer.to(parent.toCredentialedImage(destinationImage, logger));
    containerizer.setAllowInsecureRegistries(parent.insecure);
    containerizer.setToolName("jib");
    containerizer.addEventHandler(LogEvent.class, logger);

    ExecutorService executor = Executors.newCachedThreadPool();
    try {
      containerizer.setExecutorService(executor);

      JibContainer result = builder.containerize(containerizer);
      System.out.printf("Containerized to %s (%s)\n", destinationImage, result.getDigest());
      return 0;
    } finally {
      executor.shutdown();
    }
  }
}
