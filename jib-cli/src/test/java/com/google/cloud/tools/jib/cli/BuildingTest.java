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
import com.google.common.collect.Sets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Assert;
import org.junit.Test;
import picocli.CommandLine;

/** Tests for {@link Building}. */
public class BuildingTest {

  @Test
  public void testShortForms_creationTime() {
    Building fixture =
        CommandLine.populateCommand(new Building() {}, "-d", "-c", "1970-01-01T00:00:01Z");
    Assert.assertEquals(Instant.EPOCH.plus(Duration.ofSeconds(1)), fixture.creationTime);
  }

  @Test
  public void testShortForms_entrypoint() {
    Building fixture = CommandLine.populateCommand(new Building() {}, "-d", "-e", "cmd,arg1");
    Assert.assertEquals(Arrays.asList("cmd", "arg1"), fixture.entrypoint);
  }

  @Test
  public void testShortForms_arguments() {
    Building fixture = CommandLine.populateCommand(new Building() {}, "-d", "-a", "arg1,arg2");
    Assert.assertEquals(Arrays.asList("arg1", "arg2"), fixture.arguments);
  }

  @Test
  public void testShortForms_environment() {
    Building fixture = CommandLine.populateCommand(new Building() {}, "-d", "-E", "VERBOSE=false");
    Assert.assertEquals(Collections.singletonMap("VERBOSE", "false"), fixture.environment);
  }

  @Test
  public void testShortForms_labels() {
    Building fixture = CommandLine.populateCommand(new Building() {}, "-d", "-l", "source=github");
    Assert.assertEquals(Collections.singletonMap("source", "github"), fixture.labels);
  }

  @Test
  public void testShortForms_docker() {
    Building fixture = CommandLine.populateCommand(new Building() {}, "-d");
    Assert.assertTrue(fixture.pushMode.toDocker);
    Assert.assertFalse(fixture.pushMode.toRegistry);
  }

  @Test
  public void testShortForms_registry() {
    Building fixture = CommandLine.populateCommand(new Building() {}, "-r");
    Assert.assertFalse(fixture.pushMode.toDocker);
    Assert.assertTrue(fixture.pushMode.toRegistry);
  }

  @Test
  public void testShortForms_ports() {
    Building fixture =
        CommandLine.populateCommand(new Building() {}, "-d", "-p", "25-26/tcp", "-p", "30/udp");
    Assert.assertEquals(
        Sets.newHashSet(Port.tcp(25), Port.tcp(26), Port.udp(30)), new HashSet<>(fixture.ports));
  }

  @Test
  public void testShortForms_volume() {
    Building fixture =
        CommandLine.populateCommand(new Building() {}, "-d", "-V", "/foo", "-V", "/bar");
    Assert.assertEquals(
        Sets.newHashSet(AbsoluteUnixPath.get("/foo"), AbsoluteUnixPath.get("/bar")),
        new HashSet<>(fixture.volumes));
  }

  @Test
  public void testShortForms_user() {
    Building fixture = CommandLine.populateCommand(new Building() {}, "-d", "-u", "foo");
    Assert.assertEquals("foo", fixture.user);
  }

  @Test
  public void testIncomplete() {
    try {
      CommandLine.populateCommand(new Building() {});
      Assert.fail("should have errored with incomplete arguments");
    } catch (CommandLine.MissingParameterException ex) {
      Assert.assertEquals(
          "Error: Missing required argument (specify one of these): (-d | -r)", ex.getMessage());
    }
  }
}
