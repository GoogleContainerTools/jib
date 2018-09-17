/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link EventEmitter}. */
public class EventEmitterTest {

  /** Test {@link JibEvent}. */
  private static class TestJibEvent1 implements JibEvent {}

  /** Test {@link JibEvent}. */
  private static class TestJibEvent2 implements JibEvent {}

  @Test
  public void testEmit() {
    List<String> emissions = new ArrayList<>();

    EventHandlers eventHandlers =
        new EventHandlers()
            .add(
                new JibEventType<>(TestJibEvent1.class),
                testJibEvent1 -> emissions.add("handled 1 first"))
            .add(
                new JibEventType<>(TestJibEvent1.class),
                testJibEvent1 -> emissions.add("handled 1 second"))
            .add(
                new JibEventType<>(TestJibEvent2.class),
                testJibEvent2 -> emissions.add("handled 2"))
            .add(jibEvent -> emissions.add("handled generic"));

    TestJibEvent1 testJibEvent1 = new TestJibEvent1();
    TestJibEvent2 testJibEvent2 = new TestJibEvent2();

    EventEmitter eventEmitter = new EventEmitter(eventHandlers);
    eventEmitter.emit(testJibEvent1);
    eventEmitter.emit(testJibEvent2);

    Assert.assertEquals(
        Arrays.asList(
            "handled generic",
            "handled 1 first",
            "handled 1 second",
            "handled generic",
            "handled 2"),
        emissions);
  }
}
