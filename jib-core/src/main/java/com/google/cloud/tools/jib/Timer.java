/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.jib;

import java.io.Closeable;
import java.util.Stack;

/**
 * Times execution intervals. This is only for testing purposes and will be removed before the first
 * release.
 */
public class Timer implements Closeable {

  private static Stack<String> labels = new Stack<>();
  private static Stack<Long> times = new Stack<>();

  private static StringBuilder log = new StringBuilder();

  public static void print() {
    System.out.println(log);
  }

  public static Timer push(String label) {
    logTabs();
    log.append("TIMING\t");
    log.append(label);
    log.append("\n");
    labels.push(label);
    times.push(System.currentTimeMillis());
    return new Timer();
  }

  public static void time(String label) {
    pop();
    push(label);
  }

  private static void pop() {
    String label = labels.pop();
    long time = times.pop();
    logTabs();
    log.append("TIMED\t");
    log.append(label);
    log.append(" : ");
    log.append(System.currentTimeMillis() - time);
    log.append("\n");
  }

  private static void logTabs() {
    for (int i = 0; i < labels.size(); i++) {
      log.append("\t");
    }
  }

  @Override
  public void close() {
    Timer.pop();
  }
}
