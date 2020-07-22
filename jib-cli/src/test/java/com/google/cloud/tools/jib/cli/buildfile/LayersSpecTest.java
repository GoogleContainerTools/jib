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
import java.nio.file.Paths;
import java.time.Instant;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link LayersSpec}. */
public class LayersSpecTest {

  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @Test
  public void testLayersSpec_full() throws JsonProcessingException {
    String data =
        "entries:\n" // trivial layer
            + "  - name: some layer\n"
            + "    archive: /something.tgz\n"
            + "properties:\n" // trivial file properties spec
            + "  timestamp: 1\n";

    LayersSpec parsed = mapper.readValue(data, LayersSpec.class);
    Assert.assertEquals("some layer", ((ArchiveLayerSpec) parsed.getEntries().get(0)).getName());
    Assert.assertEquals(
        Paths.get("/something.tgz"), ((ArchiveLayerSpec) parsed.getEntries().get(0)).getArchive());
    Assert.assertEquals(Instant.ofEpochMilli(1), parsed.getProperties().get().getTimestamp().get());
  }

  @Test
  public void testLayersSpec_entriesRequired() {
    String data =
        "properties:\n" // trivial file properties spec
            + "  timestamp: 1\n";

    try {
      mapper.readValue(data, LayersSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(), CoreMatchers.startsWith("Missing required creator property 'entries'"));
    }
  }

  @Test
  public void testLayersSpec_entriesNotNull() {
    String data =
        "entries: null\n"
            + "properties:\n" // trivial file properties spec
            + "  timestamp: 1\n";

    try {
      mapper.readValue(data, LayersSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(), CoreMatchers.containsString("Property 'entries' cannot be null"));
    }
  }

  @Test
  public void testLayersSpec_entriesNotEmpty() {
    String data =
        "entries: []\n"
            + "properties:\n" // trivial file properties spec
            + "  timestamp: 1\n";

    try {
      mapper.readValue(data, LayersSpec.class);
      Assert.fail();
    } catch (JsonProcessingException jpe) {
      MatcherAssert.assertThat(
          jpe.getMessage(),
          CoreMatchers.containsString("Property 'entries' cannot be an empty collection"));
    }
  }
}
