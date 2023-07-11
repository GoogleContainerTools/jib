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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import java.util.List;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link ProgressEventDispatcher}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProgressEventDispatcherTest {

  @Mock private EventHandlers mockEventHandlers;

  @Test
  void testDispatch() {
    try (ProgressEventDispatcher progressEventDispatcher =
            ProgressEventDispatcher.newRoot(mockEventHandlers, "ignored", 10);
        ProgressEventDispatcher ignored =
            progressEventDispatcher.newChildProducer().create("ignored", 20)) {
      // empty
    }

    ArgumentCaptor<ProgressEvent> progressEventArgumentCaptor =
        ArgumentCaptor.forClass(ProgressEvent.class);
    Mockito.verify(mockEventHandlers, Mockito.times(4))
        .dispatch(progressEventArgumentCaptor.capture());
    List<ProgressEvent> progressEvents = progressEventArgumentCaptor.getAllValues();

    Assert.assertSame(progressEvents.get(0).getAllocation(), progressEvents.get(3).getAllocation());
    Assert.assertSame(progressEvents.get(1).getAllocation(), progressEvents.get(2).getAllocation());

    Assert.assertEquals(0, progressEvents.get(0).getUnits());
    Assert.assertEquals(0, progressEvents.get(1).getUnits());
    Assert.assertEquals(20, progressEvents.get(2).getUnits());
    Assert.assertEquals(9, progressEvents.get(3).getUnits());
  }

  @Test
  void testDispatch_safeWithtooMuchProgress() {
    try (ProgressEventDispatcher progressEventDispatcher =
        ProgressEventDispatcher.newRoot(mockEventHandlers, "allocation description", 10)) {
      progressEventDispatcher.dispatchProgress(6);
      progressEventDispatcher.dispatchProgress(8);
      progressEventDispatcher.dispatchProgress(1);
    }

    ArgumentCaptor<ProgressEvent> eventsCaptor = ArgumentCaptor.forClass(ProgressEvent.class);
    Mockito.verify(mockEventHandlers, Mockito.times(4)).dispatch(eventsCaptor.capture());
    List<ProgressEvent> progressEvents = eventsCaptor.getAllValues();

    Assert.assertSame(progressEvents.get(0).getAllocation(), progressEvents.get(1).getAllocation());
    Assert.assertSame(progressEvents.get(1).getAllocation(), progressEvents.get(2).getAllocation());
    Assert.assertSame(progressEvents.get(2).getAllocation(), progressEvents.get(3).getAllocation());

    Assert.assertEquals(10, progressEvents.get(0).getAllocation().getAllocationUnits());

    Assert.assertEquals(0, progressEvents.get(0).getUnits());
    Assert.assertEquals(6, progressEvents.get(1).getUnits());
    Assert.assertEquals(4, progressEvents.get(2).getUnits());
    Assert.assertEquals(0, progressEvents.get(3).getUnits());
  }

  @Test
  void testDispatch_safeWithTooManyChildren() {
    try (ProgressEventDispatcher progressEventDispatcher =
            ProgressEventDispatcher.newRoot(mockEventHandlers, "allocation description", 1);
        ProgressEventDispatcher ignored1 =
            progressEventDispatcher.newChildProducer().create("ignored", 5);
        ProgressEventDispatcher ignored2 =
            progressEventDispatcher.newChildProducer().create("ignored", 4)) {
      // empty
    }

    ArgumentCaptor<ProgressEvent> eventsCaptor = ArgumentCaptor.forClass(ProgressEvent.class);
    Mockito.verify(mockEventHandlers, Mockito.times(5)).dispatch(eventsCaptor.capture());
    List<ProgressEvent> progressEvents = eventsCaptor.getAllValues();

    Assert.assertEquals(1, progressEvents.get(0).getAllocation().getAllocationUnits());
    Assert.assertEquals(5, progressEvents.get(1).getAllocation().getAllocationUnits());
    Assert.assertEquals(4, progressEvents.get(2).getAllocation().getAllocationUnits());

    // child1 (of allocation 5) opening and closing
    Assert.assertSame(progressEvents.get(1).getAllocation(), progressEvents.get(4).getAllocation());
    // child1 (of allocation 4) opening and closing
    Assert.assertSame(progressEvents.get(2).getAllocation(), progressEvents.get(3).getAllocation());

    Assert.assertEquals(0, progressEvents.get(0).getUnits()); // 0-progress sent when root creation
    Assert.assertEquals(0, progressEvents.get(1).getUnits()); // when child1 creation
    Assert.assertEquals(0, progressEvents.get(2).getUnits()); // when child2 creation
    Assert.assertEquals(4, progressEvents.get(3).getUnits()); // when child2 closes
    Assert.assertEquals(5, progressEvents.get(4).getUnits()); // when child1 closes
  }
}
