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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link LayerSpec}. */
public class LayerSpecTest {

  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @Test
  public void deserialize_toFileLayer() throws JsonProcessingException {
    String data =
        "name: layer name\n"
            + "files:\n" // trivial copy spec
            + "  - src: source\n"
            + "    dest: /dest\n";

    LayerSpec layerSpec = mapper.readValue(data, LayerSpec.class);
    MatcherAssert.assertThat(layerSpec, CoreMatchers.instanceOf(FileLayerSpec.class));
  }

  @Test
  public void deserialize_toArchiveLayer() throws JsonProcessingException {
    String data = "name: layer name\n" + "archive: out/archive.tgz\n";

    LayerSpec layerSpec = mapper.readValue(data, LayerSpec.class);
    MatcherAssert.assertThat(layerSpec, CoreMatchers.instanceOf(ArchiveLayerSpec.class));
  }

  @Test
  public void deserialize_error() {
    String data = "name: layer name\n";

    try {
      mapper.readValue(data, LayerSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(),
          CoreMatchers.endsWith("Could not parse entry into ArchiveLayer or FileLayer"));
    }
  }

  @Test
  public void deserialize_nameMissing() {
    String data = "archive: out/archive.tgz\n";

    try {
      mapper.readValue(data, LayerSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(),
          CoreMatchers.endsWith("Could not parse layer entry, missing required property 'name'"));
    }
  }
}
