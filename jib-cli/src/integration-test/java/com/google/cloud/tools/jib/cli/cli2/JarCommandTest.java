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

package com.google.cloud.tools.jib.cli.cli2;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.Command;
import com.google.common.io.Resources;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import picocli.CommandLine;

public class JarCommandTest {

  private static final Integer SUCCESS_CODE = 0;
  private static final Integer FAILURE_CODE = 1;

  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;
  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();

  @Before
  public void setUp() {
    out.reset();
    err.reset();
    System.setOut(new PrintStream(out));
    System.setErr(new PrintStream(err));
  }

  @After
  public void tearDown() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  @Test
  public void testJar_invalidFileInputs() {
    Integer executeResultForFile =
        new CommandLine(new JibCli())
            .execute("--target", "docker://jib-cli-image", "jar", "unknown.jar");

    assertThat(executeResultForFile).isEqualTo(FAILURE_CODE);
    assertThat(err.toString())
        .contains("[ERROR] The file path provided does not exist: unknown.jar");
  }

  @Test
  public void testJar_directoryFound() throws URISyntaxException {
    Path jarFile = Paths.get(Resources.getResource("emptyDir").toURI());
    Integer actual =
        new CommandLine(new JibCli())
            .execute("--target", "docker://jib-cli-image", "jar", jarFile.toString());

    assertThat(actual).isEqualTo(FAILURE_CODE);
    assertThat(err.toString())
        .contains(
            "[ERROR] The file path provided is for a directory. Please provide a path to a jar file: "
                + jarFile.toString());
  }

  @Test
  public void testJar_dockerDaemon() throws IOException, InterruptedException, URISyntaxException {
    Path jarFile = Paths.get(Resources.getResource("simpleJar.jar").toURI());
    Integer actual =
        new CommandLine(new JibCli())
            .execute("--target", "docker://jib-cli-image", "jar", jarFile.toString());
    String output = new Command("docker", "run", "--rm", "jib-cli-image").run();
    assertThat(actual).isEqualTo(SUCCESS_CODE);
    assertThat(output).isEqualTo("Hello World");
  }
}
