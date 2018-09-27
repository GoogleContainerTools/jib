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

package com.google.cloud.tools.jib.cache;

import com.google.cloud.tools.jib.cache.LayerEntriesSelector.LayerEntryTemplate;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link LayerEntriesSelector}. */
public class LayerEntriesSelectorTest {

  private static final LayerEntry TEST_LAYER_ENTRY_1 =
      new LayerEntry(Paths.get("source", "file"), AbsoluteUnixPath.get("/extraction/path"));
  private static final LayerEntry TEST_LAYER_ENTRY_2 =
      new LayerEntry(Paths.get("source", "file", "two"), AbsoluteUnixPath.get("/extraction/path"));
  private static final LayerEntry TEST_LAYER_ENTRY_3 =
      new LayerEntry(Paths.get("source", "gile"), AbsoluteUnixPath.get("/extraction/path"));
  private static final LayerEntry TEST_LAYER_ENTRY_4 =
      new LayerEntry(Paths.get("source", "gile"), AbsoluteUnixPath.get("/extraction/patha"));

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
  public void testToSortedJsonTemplates() {
    Assert.assertEquals(
        toLayerEntryTemplates(IN_ORDER_LAYER_ENTRIES),
        LayerEntriesSelector.toSortedJsonTemplates(OUT_OF_ORDER_LAYER_ENTRIES));
  }

  @Test
  public void testGenerateSelector_empty() throws IOException {
    DescriptorDigest expectedSelector =
        JsonTemplateMapper.toBlob(ImmutableList.of())
            .writeTo(ByteStreams.nullOutputStream())
            .getDigest();
    Assert.assertEquals(
        expectedSelector, LayerEntriesSelector.generateSelector(ImmutableList.of()));
  }

  @Test
  public void testGenerateSelector() throws IOException {
    DescriptorDigest expectedSelector =
        JsonTemplateMapper.toBlob(toLayerEntryTemplates(IN_ORDER_LAYER_ENTRIES))
            .writeTo(ByteStreams.nullOutputStream())
            .getDigest();
    Assert.assertEquals(
        expectedSelector, LayerEntriesSelector.generateSelector(OUT_OF_ORDER_LAYER_ENTRIES));
  }
}
