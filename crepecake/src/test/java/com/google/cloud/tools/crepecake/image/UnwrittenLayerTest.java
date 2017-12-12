/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.image;

import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestException;
import java.security.NoSuchAlgorithmException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests for {@link UnwrittenLayer}. */
public class UnwrittenLayerTest {

  @Rule public TemporaryFolder fakeFolder = new TemporaryFolder();

  @Mock private Blob mockCompressedBlob;

  @Mock private Blob mockUncompressedBlob;

  @Mock private BlobDescriptor mockBlobDescriptor;

  @Mock private DescriptorDigest mockDiffId;

  @Before
  public void setUpMocks() throws IOException, DigestException {
    MockitoAnnotations.initMocks(this);

    Mockito.when(mockCompressedBlob.writeTo(Mockito.any(OutputStream.class)))
        .thenReturn(mockBlobDescriptor);
    Mockito.when(mockUncompressedBlob.writeTo(Mockito.any(OutputStream.class)))
        .thenReturn(mockBlobDescriptor);
    Mockito.when(mockBlobDescriptor.getDigest()).thenReturn(mockDiffId);
  }

  @Test
  public void testWriteTo() throws IOException, DigestException, NoSuchAlgorithmException {
    File testFile = fakeFolder.newFile("fakefile");

    UnwrittenLayer unwrittenLayer = new UnwrittenLayer(mockCompressedBlob, mockUncompressedBlob);

    CachedLayer cachedLayer = unwrittenLayer.writeTo(testFile);

    Mockito.verify(mockCompressedBlob).writeTo(Mockito.any(OutputStream.class));
    Mockito.verify(mockUncompressedBlob).writeTo(ByteStreams.nullOutputStream());

    Assert.assertEquals(mockBlobDescriptor, cachedLayer.getBlobDescriptor());
    Assert.assertEquals(mockDiffId, cachedLayer.getDiffId());
  }
}
