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
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.Port;
import com.google.cloud.tools.jib.cli.Cram;
import com.google.common.collect.Sets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Assert;
import org.junit.Test;
import picocli.CommandLine;

/** Tests for {@link Cram}. */
public class CramTest {

  @Test
  public void testPortParsing_integer() throws Exception {
    Assert.assertEquals(Collections.singleton(Port.tcp(25)), new Cram.PortParser().convert("25"));
  }

  @Test
  public void testPortParsing_integerWithProtocol() throws Exception {
    Assert.assertEquals(
        Collections.singleton(Port.tcp(25)), new Cram.PortParser().convert("25/tcp"));
  }

  @Test
  public void testPortParsing_range() throws Exception {
    Assert.assertEquals(
        Sets.newHashSet(Port.tcp(25), Port.tcp(26)), new Cram.PortParser().convert("25-26"));
  }

  @Test
  public void testPortParsing_rangeWithProtocol() throws Exception {
    Assert.assertEquals(
        Sets.newHashSet(Port.tcp(25), Port.tcp(26)), new Cram.PortParser().convert("25-26/tcp"));
  }

  @Test
  public void testImageParsing_distroless() throws Exception {
    Assert.assertEquals(
        "gcr.io/distroless/java",
        new Cram.ImageReferenceParser().convert("gcr.io/distroless/java").toString());
  }

  @Test
  public void testImageParsing_scratch() throws Exception {
    Assert.assertEquals(
        ImageReference.scratch().toString(),
        new Cram.ImageReferenceParser().convert("scratch").toString());
  }

  @Test
  public void testPathParsing_scratch() throws Exception {
    Assert.assertEquals(AbsoluteUnixPath.get("/foo"), new Cram.PathParser().convert("/foo"));
  }

  @Test
  public void testShortForms_creationTime() {
    Cram fixture =
        CommandLine.populateCommand(new Cram(), "-c", "1970-01-01T00:00:01Z", "scratch", "foo");
    Assert.assertEquals(Instant.EPOCH.plus(Duration.ofSeconds(1)), fixture.creationTime);
  }

  @Test
  public void testShortForms_entrypoint() {
    Cram fixture = CommandLine.populateCommand(new Cram(), "-e", "cmd,arg1", "scratch", "foo");
    Assert.assertEquals(Arrays.asList("cmd", "arg1"), fixture.entrypoint);
  }

  @Test
  public void testShortForms_arguments() {
    Cram fixture = CommandLine.populateCommand(new Cram(), "-a", "arg1,arg2", "scratch", "foo");
    Assert.assertEquals(Arrays.asList("arg1", "arg2"), fixture.arguments);
  }

  @Test
  public void testShortForms_environment() {
    Cram fixture = CommandLine.populateCommand(new Cram(), "-E", "VERBOSE=false", "scratch", "foo");
    Assert.assertEquals(Collections.singletonMap("VERBOSE", "false"), fixture.environment);
  }

  @Test
  public void testShortForms_labels() {
    Cram fixture = CommandLine.populateCommand(new Cram(), "-l", "source=github", "scratch", "foo");
    Assert.assertEquals(Collections.singletonMap("source", "github"), fixture.labels);
  }

  @Test
  public void testShortForms_credentialHelpers() {
    Cram fixture = CommandLine.populateCommand(new Cram(), "-C", "gcr", "scratch", "foo");
    Assert.assertEquals(Collections.singletonList("gcr"), fixture.credentialHelpers);
  }

  @Test
  public void testShortForms_insecureRegistries() {
    Cram fixture = CommandLine.populateCommand(new Cram(), "-k", "scratch", "foo");
    Assert.assertTrue(fixture.insecure);
  }

  @Test
  public void testShortForms_verbose() {
    Cram fixture = CommandLine.populateCommand(new Cram(), "-v", "scratch", "foo");
    Assert.assertTrue(fixture.verbose);
  }

  @Test
  public void testShortForms_docker() {
    Cram fixture = CommandLine.populateCommand(new Cram(), "-d", "scratch", "foo");
    Assert.assertTrue(fixture.toDocker);
  }

  @Test
  public void testShortForms_registry() {
    Cram fixture = CommandLine.populateCommand(new Cram(), "-r", "scratch", "foo");
    Assert.assertTrue(fixture.toRegistry);
  }

  @Test
  public void testShortForms_ports() {
    Cram fixture =
        CommandLine.populateCommand(
            new Cram(), "-p", "25-26/tcp", "-p", "30/udp", "scratch", "foo");
    Assert.assertEquals(
        Sets.newHashSet(Port.tcp(25), Port.tcp(26), Port.udp(30)), new HashSet<>(fixture.ports));
  }

  @Test
  public void testShortForms_volume() {
    Cram fixture =
        CommandLine.populateCommand(new Cram(), "-V", "/foo", "-V", "/bar", "scratch", "foo");
    Assert.assertEquals(
        Sets.newHashSet(AbsoluteUnixPath.get("/foo"), AbsoluteUnixPath.get("/bar")),
        new HashSet<>(fixture.volumes));
  }

  @Test
  public void testShortForms_user() {
    Cram fixture = CommandLine.populateCommand(new Cram(), "-u", "foo", "scratch", "foo");
    Assert.assertEquals("foo", fixture.user);
  }

  @Test
  public void testIncomplete() {
    try {
      CommandLine.populateCommand(new Cram());
      Assert.fail("should have errored with incomplete arguments");
    } catch (CommandLine.MissingParameterException ex) {
      Assert.assertEquals(
          "Missing required parameters: base-image, destination-image", ex.getMessage());
    }
  }
}
