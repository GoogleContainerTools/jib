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

package com.google.cloud.tools.jib.cli.buildfile;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/**
 * Polymorphic yaml LayerSpec interface with custom deserializer, can parse both {@link
 * ArchiveLayerSpec} and {@link FileLayerSpec}.
 */
@JsonDeserialize(using = LayerSpec.Deserializer.class)
public interface LayerSpec {
  class Deserializer extends StdDeserializer<LayerSpec> {

    public Deserializer() {
      super(LayerSpec.class);
    }

    /**
     * Deserializes to {@link ArchiveLayerSpec} if yaml contains "archive" field, to {@link
     * FileLayerSpec} if yaml contains "files" field or throws {@link IOException} if neither is
     * found or no "name" was specified for the layer entry.
     */
    @Override
    public LayerSpec deserialize(JsonParser jsonParser, DeserializationContext context)
        throws IOException {
      JsonNode node = jsonParser.getCodec().readTree(jsonParser);
      if (!node.has("name")) {
        throw new IOException("Could not parse layer entry, missing required property 'name'");
      }
      if (node.has("archive")) {
        return jsonParser.getCodec().treeToValue(node, ArchiveLayerSpec.class);
      }
      if (node.has("files")) {
        return jsonParser.getCodec().treeToValue(node, FileLayerSpec.class);
      }
      throw new IOException("Could not parse entry into ArchiveLayer or FileLayer");
    }
  }
}
