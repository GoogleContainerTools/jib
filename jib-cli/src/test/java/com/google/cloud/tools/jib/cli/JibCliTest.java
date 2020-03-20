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

import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.common.collect.Sets;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import picocli.CommandLine;

/** Tests for {@link JibCli}. */
public class JibCliTest {

  @Test
  public void testPortParsing_integer() throws Exception {
    Assert.assertEquals(Collections.singleton(Port.tcp(25)), new JibCli.PortParser().convert("25"));
  }

  @Test
  public void testPortParsing_integerWithProtocol() throws Exception {
    Assert.assertEquals(
        Collections.singleton(Port.tcp(25)), new JibCli.PortParser().convert("25/tcp"));
  }

  @Test
  public void testPortParsing_range() throws Exception {
    Assert.assertEquals(
        Sets.newHashSet(Port.tcp(25), Port.tcp(26)), new JibCli.PortParser().convert("25-26"));
  }

  @Test
  public void testPortParsing_rangeWithProtocol() throws Exception {
    Assert.assertEquals(
        Sets.newHashSet(Port.tcp(25), Port.tcp(26)), new JibCli.PortParser().convert("25-26/tcp"));
  }

  @Test
  public void testImageParsing_distroless() throws Exception {
    Assert.assertEquals(
        "gcr.io/distroless/java",
        new JibCli.ImageReferenceParser().convert("gcr.io/distroless/java").toString());
  }

  @Test
  public void testImageParsing_scratch() throws Exception {
    Assert.assertEquals(
        ImageReference.scratch().toString(),
        new JibCli.ImageReferenceParser().convert("scratch").toString());
  }

  @Test
  public void testPathParsing_scratch() throws Exception {
    Assert.assertEquals(AbsoluteUnixPath.get("/foo"), new JibCli.PathParser().convert("/foo"));
  }

  @Test
  public void testShortForms_credentialHelpers() {
    JibCli fixture = CommandLine.populateCommand(new JibCli(), "-C", "gcr");
    Assert.assertEquals(Collections.singletonList("gcr"), fixture.credentialHelpers);
  }

  @Test
  public void testShortForms_insecureRegistries() {
    JibCli fixture = CommandLine.populateCommand(new JibCli(), "-k");
    Assert.assertTrue(fixture.insecure);
  }

  @Test
  public void testShortForms_verbose() {
    JibCli fixture = CommandLine.populateCommand(new JibCli(), "-v");
    Assert.assertTrue(fixture.verbose);
  }
}
