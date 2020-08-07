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

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.FilePermissionsProvider;
import com.google.cloud.tools.jib.api.buildplan.ModificationTimeProvider;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import picocli.CommandLine;

/**
 * Parses a set of layer definitions of the form of
 *
 * <pre>
 * layerConfig := layerSpec (";" layerSpec)*
 * layerSpec := localPath ("," containerPath ("," directive)*)?
 * directive := ("name=" string{layer-name})
 *         | (("p" | "permissions") "=" ("actual" | (octal{files} (":" octal{folders})?)))
 *         | (("ts" | "timestamps") "=" ("actual" | integer{since-epoch} | iso8601-date-time))
 * </pre>
 *
 * <p>If the {@code containerPath} is unspecified, it is treated as equivalent to the container root
 * ({@code /}).
 */
class LayerDefinitionParser implements CommandLine.ITypeConverter<FileEntriesLayer> {

  @Override
  public FileEntriesLayer convert(String layerDefinition) throws Exception {
    FileEntriesLayer.Builder layerBuilder = FileEntriesLayer.builder();
    for (String specification : layerDefinition.split(";", -1)) {
      parseSpecification(layerBuilder, specification);
    }
    return layerBuilder.build();
  }

  private void parseSpecification(FileEntriesLayer.Builder layerBuilder, String subspecification)
      throws IOException {
    FilePermissionsProvider permissionsProvider =
        FileEntriesLayer.DEFAULT_FILE_PERMISSIONS_PROVIDER;
    ModificationTimeProvider timestampProvider =
        FileEntriesLayer.DEFAULT_MODIFICATION_TIME_PROVIDER;

    String[] definition = subspecification.split(",", -1);
    String containerRoot = definition.length == 1 ? "/" : definition[1];
    for (int i = 2; i < definition.length; i++) {
      String[] directive = definition[i].split("=", 2);
      switch (directive[0]) {
        case "permissions":
        case "p":
          if (directive.length == 1) {
            throw new CommandLine.TypeConversionException("missing permissions configuration");
          }
          permissionsProvider = parsePermissionsDirective(directive[1]);
          break;

        case "timestamps":
        case "ts":
          if (directive.length == 1) {
            throw new CommandLine.TypeConversionException("missing timestamps configuration");
          }
          timestampProvider = parseTimestampsDirective(directive[1]);
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
  static ModificationTimeProvider parseTimestampsDirective(String directive) {
    if ("actual".equals(directive)) {
      return new ActualTimestampProvider();
    }

    // treat as seconds since epoch
    if (directive.matches("\\d+")) {
      long secondsSinceEpoch = Long.parseLong(directive);
      return new FixedTimestampProvider(Instant.ofEpochSecond(secondsSinceEpoch));
    }
    return new FixedTimestampProvider(
        DateTimeFormatter.ISO_DATE_TIME.parse(directive, Instant::from));
  }

  @VisibleForTesting
  static FilePermissionsProvider parsePermissionsDirective(String directive) {
    if ("actual".equals(directive)) {
      return new ActualPermissionsProvider();
    }

    String[] spec = directive.split(":", -1);
    FilePermissions filesPermission = FilePermissions.fromOctalString(spec[0]);
    FilePermissions directoriesPermission = FilePermissions.DEFAULT_FOLDER_PERMISSIONS;
    if (spec.length > 1) {
      directoriesPermission = FilePermissions.fromOctalString(spec[1]);
    }
    return new FixedPermissionsProvider(filesPermission, directoriesPermission);
  }
}
