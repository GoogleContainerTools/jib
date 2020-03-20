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

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.cli.JibCli.PathParser;
import com.google.cloud.tools.jib.cli.JibCli.PortParser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/** Base class for subcommands that build images. */
abstract class Building {
  @ParentCommand
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  protected JibCli parent;

  protected static class PushMode {
    @Option(
        names = {"-d", "--docker"},
        description = "Load result to local Docker daemon",
        required = true)
    boolean toDocker = false;

    @Option(
        names = {"-r", "--registry"},
        description = "Push to registry",
        required = true)
    boolean toRegistry = false;
  }

  @ArgGroup(exclusive = true, multiplicity = "1") // mutually-exclusive options
  @SuppressWarnings("NullAway.Init")
  protected PushMode pushMode;

  @Option(
      names = {"-c", "--creation-time"},
      paramLabel = "time",
      description = "Set the image creation time (default: 1970-01-01T00:00:00Z)")
  @Nullable
  protected Instant creationTime;

  @Option(
      names = {"-p", "--port"},
      split = ",",
      paramLabel = "port",
      description = "Expose port/type (ex: 25 or 25/tcp)",
      converter = PortParser.class)
  @Nullable
  protected List<Port> ports;

  @Option(
      names = {"-V", "--volume"},
      split = ",",
      paramLabel = "path",
      description = "Configure specified paths as volumes",
      converter = PathParser.class)
  @Nullable
  protected List<AbsoluteUnixPath> volumes;

  @Option(
      names = {"-u", "--user"},
      paramLabel = "user",
      description = "Set user for execution (uid or existing user id)")
  @Nullable
  protected String user;

  @Option(
      names = {"-e", "--entrypoint"},
      paramLabel = "arg",
      split = ",",
      hideParamSyntax = true,
      description = "Set the container entrypoint")
  @Nullable
  protected List<String> entrypoint;

  @Option(
      names = {"-a", "--arguments"},
      split = ",",
      paramLabel = "arg",
      hideParamSyntax = true,
      description = "Set the container entrypoint's default arguments")
  @Nullable
  protected List<String> arguments;

  @Option(
      names = {"-E", "--environment"},
      split = ",",
      paramLabel = "key=value",
      description = "Add environment pairs")
  @Nullable
  protected Map<String, String> environment;

  @Option(
      names = {"-l", "--label"},
      split = ",",
      paramLabel = "key=value",
      description = "Add image labels")
  @Nullable
  protected Map<String, String> labels;

  protected void verbose(String message) {
    if (parent.verbose) {
      System.out.println(message);
    }
  }
}
