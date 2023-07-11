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

import com.google.cloud.tools.jib.api.JibEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Tests for {@link EventHandlers}. */
class EventHandlersTest {

  /** Test {@link JibEvent}. */
  private interface TestJibEvent1 extends JibEvent {

    String getPayload();
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

  /** Test {@link JibEvent}. */
  private static class TestJibEvent3 implements JibEvent {}

  @Test
  void testAdd() {
    int[] counter = new int[1];
    EventHandlers eventHandlers =
        EventHandlers.builder()
            .add(
                TestJibEvent1.class,
                testJibEvent1 -> Assert.assertEquals("payload", testJibEvent1.getPayload()))
            .add(TestJibEvent2.class, testJibEvent2 -> testJibEvent2.sayHello("Jib"))
            .add(JibEvent.class, jibEvent -> counter[0]++)
            .build();
    Assert.assertTrue(eventHandlers.getHandlers().containsKey(JibEvent.class));
    Assert.assertTrue(eventHandlers.getHandlers().containsKey(TestJibEvent1.class));
    Assert.assertTrue(eventHandlers.getHandlers().containsKey(TestJibEvent2.class));
    Assert.assertEquals(1, eventHandlers.getHandlers().get(JibEvent.class).size());
    Assert.assertEquals(1, eventHandlers.getHandlers().get(TestJibEvent1.class).size());
    Assert.assertEquals(1, eventHandlers.getHandlers().get(TestJibEvent2.class).size());

    TestJibEvent1 mockTestJibEvent1 = Mockito.mock(TestJibEvent1.class);
    Mockito.when(mockTestJibEvent1.getPayload()).thenReturn("payload");
    TestJibEvent2 testJibEvent2 = new TestJibEvent2();

    // Checks that the handlers handled their respective event types.
    eventHandlers.getHandlers().get(JibEvent.class).asList().get(0).handle(mockTestJibEvent1);
    eventHandlers.getHandlers().get(JibEvent.class).asList().get(0).handle(testJibEvent2);
    eventHandlers.getHandlers().get(TestJibEvent1.class).asList().get(0).handle(mockTestJibEvent1);
    eventHandlers.getHandlers().get(TestJibEvent2.class).asList().get(0).handle(testJibEvent2);

    Assert.assertEquals(2, counter[0]);
    Mockito.verify(mockTestJibEvent1).getPayload();
    Mockito.verifyNoMoreInteractions(mockTestJibEvent1);
    testJibEvent2.assertMessageCorrect("Jib");
  }

  @Test
  void testDispatch() {
    List<String> emissions = new ArrayList<>();

    EventHandlers eventHandlers =
        EventHandlers.builder()
            .add(TestJibEvent2.class, testJibEvent2 -> emissions.add("handled 2 first"))
            .add(TestJibEvent2.class, testJibEvent2 -> emissions.add("handled 2 second"))
            .add(TestJibEvent3.class, testJibEvent3 -> emissions.add("handled 3"))
            .add(JibEvent.class, jibEvent -> emissions.add("handled generic"))
            .build();

    TestJibEvent2 testJibEvent2 = new TestJibEvent2();
    TestJibEvent3 testJibEvent3 = new TestJibEvent3();

    eventHandlers.dispatch(testJibEvent2);
    eventHandlers.dispatch(testJibEvent3);

    Assert.assertEquals(
        Arrays.asList(
            "handled generic",
            "handled 2 first",
            "handled 2 second",
            "handled generic",
            "handled 3"),
        emissions);
  }
}
