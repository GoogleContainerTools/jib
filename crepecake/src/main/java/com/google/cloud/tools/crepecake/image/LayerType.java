package com.google.cloud.tools.crepecake.image;

import com.google.cloud.tools.crepecake.blob.BlobStream;

/** Different types of {@link Layer}s have different properties. */
public enum LayerType {

  /**
   * A layer that has not been written out and only has the unwritten content {@link BlobStream}.
   * Once written, this layer becomes a {@code CACHED} layer.
   */
  UNWRITTEN,

  /**
   * A layer that has been written out (i.e. to a cache) and has its file-backed content BLOB,
   * digest, size, and diff ID.
   */
  CACHED,

  /**
   * A layer that does not have its content BLOB. It is only referenced by its digest, size, and
   * diff ID.
   */
  REFERENCE,

  /**
   * A layer that has its content BLOB, digest, and size, but not its diff ID. The content BLOB can
   * be decompressed to get the diff ID.
   */
  REFERENCE_NO_DIFF_ID,
}
