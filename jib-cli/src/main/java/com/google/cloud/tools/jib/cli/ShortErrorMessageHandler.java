/*
 * Copyright 2021 Google LLC.
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

import java.io.PrintWriter;
import picocli.CommandLine;
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

/** Class to print a short error message when an invalid input is passed in. */
public class ShortErrorMessageHandler implements IParameterExceptionHandler {

  @Override
  public int handleParseException(ParameterException exception, String[] args) {
    CommandLine command = exception.getCommandLine();
    PrintWriter writer = command.getErr();

    // Print error message
    writer.println(exception.getMessage());
    CommandLine.UnmatchedArgumentException.printSuggestions(exception, writer);
    writer.print(command.getHelp().fullSynopsis());

    CommandSpec commandSpec = command.getCommandSpec();
    writer.printf("Run '%s --help' for more information on usage.%n", commandSpec.qualifiedName());
    return command.getExitCodeExceptionMapper() != null
        ? command.getExitCodeExceptionMapper().getExitCode(exception)
        : commandSpec.exitCodeOnInvalidInput();
  }
}
