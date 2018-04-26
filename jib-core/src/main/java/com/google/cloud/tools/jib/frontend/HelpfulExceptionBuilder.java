/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.frontend;

import java.util.concurrent.Callable;
import java.util.function.Function;

/** Builds exceptions that provides suggestions on how to fix the error. Extend by implementing with a specific wrapper exception class. */
public abstract class HelpfulExceptionBuilder<E extends Exception> {

    private final String messageHeader;

    /** @param messageHeader the initial message text for the exception message */
    public HelpfulExceptionBuilder(String messageHeader) {
      this.messageHeader = messageHeader;
    }

    /** @return an {@link E} with a cause and a suggestion */
    public E withSuggestion(Throwable cause, String suggestion) {
      StringBuilder message = getMessageHeader();
      message.append(", perhaps you should ");
      message.append(suggestion);
      return makeException(message.toString(), cause);
    }

    /** @return an {@link E} with just a cause */
    public E withNoHelp(Throwable cause) {
      return makeException(getMessageHeader().toString(), cause);
    }

    /** Implement with constructing a specific wrapper exception class. */
    protected abstract E makeException(String message, Throwable cause);

    private StringBuilder getMessageHeader() {
      return new StringBuilder(messageHeader);
    }
  }
