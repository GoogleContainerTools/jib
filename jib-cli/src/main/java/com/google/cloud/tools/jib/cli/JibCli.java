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

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import picocli.CommandLine;

@CommandLine.Command(
        name = "jib",
        versionProvider = VersionInfo.class,
        mixinStandardHelpOptions = true,
        showAtFileInUsageHelp = true,
        synopsisSubcommandLabel = "COMMAND",
        description = "A tool for creating container images",
        subcommands = {Build.class, Jar.class})
public class JibCli {

  static Logger configureHttpLogging(Level level) {
    // To instantiate the static HttpTransport logger field.
    // Fixes https://github.com/GoogleContainerTools/jib/issues/3156.
    new ApacheHttpTransport();
    ConsoleHandler consoleHandler = new ConsoleHandler();
    consoleHandler.setLevel(level);

    Logger logger = Logger.getLogger(HttpTransport.class.getName());
    logger.setLevel(level);
    logger.addHandler(consoleHandler);
    return logger;
  }

  static void logTerminatingException(
          ConsoleLogger consoleLogger, Exception exception, boolean logStackTrace) {
    if (logStackTrace) {
      StringWriter writer = new StringWriter();
      exception.printStackTrace(new PrintWriter(writer));
      consoleLogger.log(LogEvent.Level.ERROR, writer.toString());
    }

    consoleLogger.log(
            LogEvent.Level.ERROR,
            "\u001B[31;1m"
                    + exception.getClass().getName()
                    + ": "
                    + exception.getMessage()
                    + "\u001B[0m");
  }

  /**
   * The magic starts here.
   *
   * @param args the command-line arguments
   */
  public static void main(String[] args) {
    int exitCode =
            new CommandLine(new JibCli())
                    .setParameterExceptionHandler(new ShortErrorMessageHandler())
                    .execute(args);
    System.exit(exitCode);
  }
}
