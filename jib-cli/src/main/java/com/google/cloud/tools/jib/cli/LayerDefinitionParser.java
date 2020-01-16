/*
 * Copyright 2019 Google LLC.
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

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.FilePermissions;
import com.google.cloud.tools.jib.api.LayerConfiguration;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.function.BiFunction;
import picocli.CommandLine;

/**
 * Parses a layer mapping of the form of {@code
 * local-path:container-path:permissions=755,644:timestamps=actual}. A shortcut form, {@code
 * local-path} is also supported, equivalent to {@code local-path:/}.
 */
class LayerDefinitionParser implements CommandLine.ITypeConverter<LayerConfiguration> {

  @Override
  public LayerConfiguration convert(String layerDefinition) throws Exception {
    LayerConfiguration.Builder layerBuilder = LayerConfiguration.builder();
    for (String specification : layerDefinition.split(";", -1)) {
      parseSpecification(layerBuilder, specification);
    }
    return layerBuilder.build();
  }

  private void parseSpecification(LayerConfiguration.Builder layerBuilder, String subspecification)
      throws IOException {
    BiFunction<Path, AbsoluteUnixPath, FilePermissions> permissionsProvider =
        LayerConfiguration.DEFAULT_FILE_PERMISSIONS_PROVIDER;
    BiFunction<Path, AbsoluteUnixPath, Instant> timestampProvider =
        LayerConfiguration.DEFAULT_MODIFICATION_TIME_PROVIDER;

    String[] definition = subspecification.split(":", -1);
    String containerRoot = definition.length == 1 ? "/" : definition[1];
    for (int i = 2; i < definition.length; i++) {
      String[] directive = definition[i].split("=", 2);
      switch (directive[0]) {
        case "permissions":
        case "perms":
        case "p":
          if (directive.length == 1) {
            throw new CommandLine.TypeConversionException("missing permissions configuration");
          }
          permissionsProvider = configurePermissionsProvider(directive[1]);
          break;

        case "timestamps":
        case "timestamp":
        case "ts":
          if (directive.length == 1) {
            throw new CommandLine.TypeConversionException("missing timestamps configuration");
          }
          timestampProvider = configureTimestampsProvider(directive[1]);
          break;

        case "name":
          if (directive.length == 1) {
            throw new CommandLine.TypeConversionException("missing layer name");
          }
          layerBuilder.setName(directive[1]);
          break;

        default:
          throw new CommandLine.TypeConversionException(
              "unknown layer configuration directive: " + directive[0]);
      }
    }
    layerBuilder.addEntryRecursive(
        Paths.get(definition[0]),
        AbsoluteUnixPath.get(containerRoot),
        permissionsProvider,
        timestampProvider);
  }

  @VisibleForTesting
  static BiFunction<Path, AbsoluteUnixPath, Instant> configureTimestampsProvider(String directive) {
    if ("actual".equals(directive)) {
      return (local, inContainer) -> {
        try {
          return Files.getLastModifiedTime(local).toInstant();
        } catch (IOException ex) {
          System.err.printf("%s: %s\n", local, ex);
          throw new RuntimeException(ex);
        }
      };
    }

    // absolute time
    Instant fixed;
    // treat as seconds since epoch
    if (directive.matches("\\d+")) {
      long secondsSinceEpoch = Long.parseLong(directive);
      fixed = Instant.ofEpochSecond(secondsSinceEpoch);
    } else {
      fixed =
          DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).parse(directive, Instant::from);
    }
    return (local, inContainer) -> fixed;
  }

  @VisibleForTesting
  static BiFunction<Path, AbsoluteUnixPath, FilePermissions> configurePermissionsProvider(
      String directive) {
    if ("actual".equals(directive)) {
      return (local, inContainer) -> {
        try {
          return FilePermissions.fromPosixFilePermissions(Files.getPosixFilePermissions(local));
        } catch (IOException ex) {
          System.err.printf("%s: %s\n", local, ex);
          throw new RuntimeException(ex);
        }
      };
    }

    FilePermissions filesPermission = FilePermissions.DEFAULT_FILE_PERMISSIONS;
    FilePermissions directoriesPermission = FilePermissions.DEFAULT_FOLDER_PERMISSIONS;
    String[] spec = directive.split("/", -1);
    filesPermission = FilePermissions.fromOctalString(spec[0]);
    if (spec.length > 1) {
      directoriesPermission = FilePermissions.fromOctalString(spec[1]);
    }
    return new FixedPermissionsProvider(filesPermission, directoriesPermission);
  }
}
