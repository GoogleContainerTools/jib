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

package com.google.cloud.tools.jib.ncache;

import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.ncache.LayerEntriesSelector.LayerEntriesTemplate;
import com.google.cloud.tools.jib.ncache.LayerEntriesSelector.LayerEntryTemplate;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link LayerEntriesSelector}. */
public class LayerEntriesSelectorTest {

  private static final LayerEntry TEST_LAYER_ENTRY_1 =
      new LayerEntry(Paths.get("source", "file"), Paths.get("extraction", "path"));
  private static final LayerEntry TEST_LAYER_ENTRY_2 =
      new LayerEntry(Paths.get("source", "file", "two"), Paths.get("extraction", "path"));
  private static final LayerEntry TEST_LAYER_ENTRY_3 =
      new LayerEntry(Paths.get("source", "gile"), Paths.get("extraction", "path"));
  private static final LayerEntry TEST_LAYER_ENTRY_4 =
      new LayerEntry(Paths.get("source", "gile"), Paths.get("extraction", "patha"));

  private static final ImmutableList<LayerEntry> OUT_OF_ORDER_LAYER_ENTRIES =
      ImmutableList.of(
          TEST_LAYER_ENTRY_4, TEST_LAYER_ENTRY_2, TEST_LAYER_ENTRY_3, TEST_LAYER_ENTRY_1);
  private static final ImmutableList<LayerEntry> IN_ORDER_LAYER_ENTRIES =
      ImmutableList.of(
          TEST_LAYER_ENTRY_1, TEST_LAYER_ENTRY_2, TEST_LAYER_ENTRY_3, TEST_LAYER_ENTRY_4);

  private static ImmutableList<LayerEntryTemplate> toLayerEntryTemplates(
      ImmutableList<LayerEntry> layerEntries) {
    return layerEntries
        .stream()
        .map(LayerEntryTemplate::new)
        .collect(ImmutableList.toImmutableList());
  }

  @Test
  public void testLayerEntryTemplate_compareTo() {
    Assert.assertEquals(
        toLayerEntryTemplates(IN_ORDER_LAYER_ENTRIES),
        ImmutableList.sortedCopyOf(toLayerEntryTemplates(OUT_OF_ORDER_LAYER_ENTRIES)));
  }

  @Test
  public void testLayerEntriesTemplate_getList() {
    Assert.assertEquals(
        toLayerEntryTemplates(IN_ORDER_LAYER_ENTRIES),
        new LayerEntriesTemplate(OUT_OF_ORDER_LAYER_ENTRIES).getList());
  }

  @Test
  public void testGenerateSelector_empty() throws IOException {
    DescriptorDigest expectedSelector =
        JsonTemplateMapper.toBlob(new LayerEntriesTemplate(IN_ORDER_LAYER_ENTRIES))
            .writeTo(ByteStreams.nullOutputStream())
            .getDigest();
    Assert.assertEquals(
        expectedSelector, LayerEntriesSelector.generateSelector(OUT_OF_ORDER_LAYER_ENTRIES));
  }
}
