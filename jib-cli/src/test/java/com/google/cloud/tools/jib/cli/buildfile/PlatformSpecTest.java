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
import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link PlatformSpec}. */
public class PlatformSpecTest {

  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @Test
  public void testPlatformSpec_full() throws JsonProcessingException {
    String data =
        "architecture: amd64\n"
            + "os: linux\n"
            + "os.version: 1.0.0\n"
            + "os.features:\n"
            + "  - headless\n"
            + "variant: amd64v10\n"
            + "features:\n"
            + "  - sse4\n"
            + "  - aes\n";

    PlatformSpec parsed = mapper.readValue(data, PlatformSpec.class);
    Assert.assertEquals("amd64", parsed.getArchitecture().get());
    Assert.assertEquals("linux", parsed.getOs().get());
    Assert.assertEquals("1.0.0", parsed.getOsVersion().get());
    Assert.assertEquals(ImmutableList.of("headless"), parsed.getOsFeatures());
    Assert.assertEquals("amd64v10", parsed.getVariant().get());
    Assert.assertEquals(ImmutableList.of("sse4", "aes"), parsed.getFeatures());
  }

  @Test
  public void testPlatformSpec_nullCollections() throws JsonProcessingException {
    String data = "architecture: amd64\n" + "os: linux\n";

    PlatformSpec parsed = mapper.readValue(data, PlatformSpec.class);
    Assert.assertEquals(ImmutableList.of(), parsed.getOsFeatures());
    Assert.assertEquals(ImmutableList.of(), parsed.getFeatures());
  }
}
