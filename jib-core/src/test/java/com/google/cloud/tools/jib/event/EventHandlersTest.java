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

package com.google.cloud.tools.jib.event;

import javax.annotation.Nullable;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link EventHandlers}. */
public class EventHandlersTest {

  /** Test implementation of {@link JibEvent}. */
  private static class TestJibEvent1 implements JibEvent {

    private boolean called = false;

    private void assertGetPayloadCalled() {
      Assert.assertTrue(called);
    }

    private void getPayload() {
      Assert.assertFalse(called);
      called = true;
    }
  }

  /** Test implementation of {@link JibEvent}. */
  private static class TestJibEvent2 implements JibEvent {

    @Nullable private String message;

    private void assertMessageCorrect(String name) {
      Assert.assertEquals("Hello " + name, message);
    }

    private void sayHello(String name) {
      Assert.assertNull(message);
      message = "Hello " + name;
    }
  }

  /** Test class to hold mutable int. */
  private static class Counter {

    private int count = 0;
  }

  @Test
  public void testAdd() {
    Counter counter = new Counter();
    EventHandlers eventHandlers =
        new EventHandlers()
            .add(new JibEventType<>(TestJibEvent1.class), TestJibEvent1::getPayload)
            .add(
                new JibEventType<>(TestJibEvent2.class),
                testJibEvent2 -> testJibEvent2.sayHello("Jib"))
            .add(jibEvent -> counter.count++);
    Assert.assertTrue(eventHandlers.getHandlers().containsKey(JibEvent.class));
    Assert.assertTrue(eventHandlers.getHandlers().containsKey(TestJibEvent1.class));
    Assert.assertTrue(eventHandlers.getHandlers().containsKey(TestJibEvent2.class));
    Assert.assertEquals(1, eventHandlers.getHandlers().get(JibEvent.class).size());
    Assert.assertEquals(1, eventHandlers.getHandlers().get(TestJibEvent1.class).size());
    Assert.assertEquals(1, eventHandlers.getHandlers().get(TestJibEvent2.class).size());

    TestJibEvent1 testJibEvent1 = new TestJibEvent1();
    TestJibEvent2 testJibEvent2 = new TestJibEvent2();

    // Checks that the handlers handled their respective event types.
    Assert.assertTrue(eventHandlers.getHandlers().get(JibEvent.class).get(0).handle(testJibEvent1));
    Assert.assertTrue(eventHandlers.getHandlers().get(JibEvent.class).get(0).handle(testJibEvent2));
    Assert.assertTrue(
        eventHandlers.getHandlers().get(TestJibEvent1.class).get(0).handle(testJibEvent1));
    Assert.assertTrue(
        eventHandlers.getHandlers().get(TestJibEvent2.class).get(0).handle(testJibEvent2));
    Assert.assertFalse(
        eventHandlers.getHandlers().get(TestJibEvent1.class).get(0).handle(testJibEvent2));
    Assert.assertFalse(
        eventHandlers.getHandlers().get(TestJibEvent2.class).get(0).handle(testJibEvent1));

    Assert.assertEquals(2, counter.count);
    testJibEvent1.assertGetPayloadCalled();
    testJibEvent2.assertMessageCorrect("Jib");
  }
}
