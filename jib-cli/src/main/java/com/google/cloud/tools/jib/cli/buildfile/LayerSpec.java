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
     * Deserialize based on the presence of "archive", if present, consider the layer to be of type
     * {@link ArchiveLayerSpec}, else a {@link FileLayerSpec}.
     */
    @Override
    public LayerSpec deserialize(JsonParser jp, DeserializationContext txt) throws IOException {
      JsonNode n = (JsonNode) jp.getCodec().readTree(jp);
      if (n.has("archive")) {
        return jp.getCodec().treeToValue(n, ArchiveLayerSpec.class);
      }
      if (n.has("files")) {
        return jp.getCodec().treeToValue(n, FileLayerSpec.class);
      }
      throw new IOException("Could not parse entry into ArchiveLayer or FileLayer");
    }
  }
}
