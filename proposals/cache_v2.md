# Proposal: Improved cache mechanism

The tracking issue is at [#637](https://github.com/GoogleContainerTools/jib/issues/637).

## TLDR;

The current cache design is less-than-ideal and should be revamped before releasing [Jib as a library for Java](https://github.com/GoogleContainerTools/jib/issues/337).

## Current cache mechanism

In the current state (as of version `0.9.9`), Jib caches layer data in a directory on disk. Layer tarballs are stored as files in that directory, and metadata for all layers are stored in a single JSON file.

For example, the cache directory layout may look like:

```
  36a2b7401dcddc50a35aeaa81085718b9d5fbce9d607c55a1d79beec2469f9ac.tar.gz  
  c63484398b097b7e9693ac373ac95630bb8d8ad8ff90a3277e7105bb77e8e986.tar.gz
  metadata-v2.json
```

The metadata stores a list of metadata regarding each of the layers, including its diff ID (uncompressed digest), last modified time, and layer entries (the layout of the tarball including what files actually went into building the tarball).

One of the main cache queries to perform during the Jib execution is to check if a layer is up-to-date (recently stored in the cache). Currently, this mechanism works by storing the last modified time of the layer and the layer entries. Jib queries the cache for layers that match some layer entries. If the last modified time of that cached layer precedes the modification times of the files in the layer entries, then that cached layer is used; otherwise, the layer is rebuilt from the newly-modified files.

### Problems

The problems with the current cache mechanism include:
- When multiple Jib executions using the same cache run in parallel:
  - Concurrent writes to the metadata could result in corruption of the metadata file. See [#848](https://github.com/GoogleContainerTools/jib/issues/848).
  - Only the last-finishing execution will have its metadata updates written to the metadata. The other executions will have their metadata updates lost, resulting in less-than-optimal caching.
- The up-to-date check is tightly coupled to the implementation of the cache. This does not allow for easy switching of the strategy to use for up-to-date checks.
- Retrieval of layers by layer entries currently involves checking through the entire metadata and matching against the layer entries stored in the metadata. This could become slow as the cache grows in size.
- The current implementation tightly couples the actual storage implementation with the contents stored in the cache. This does not allow for easily implementing new storage engines with possibly different underlying storage systems.
- Since the metadata is only written out at the end of a Jib execution (successful or failure), interrupted Jib executions would result in lost metadata updates.

These problems should be resolved before [Jib as a library for Java](https://github.com/GoogleContainerTools/jib/issues/337) is released.

## Proposal

### Goals

- Clearly-defined storage engine interface that decouples the query system from the storage engine.
- A default storage engine implementation that stores layer data independently and can retrieve layers by the layer entries that built that layer in constant time.

### Solution

#### Storage engine interface

There are 4 actions that would be performed against the storage engine:

- Save a cache entry
- List the entries in the cache
- Retrieve a cache entry by layer digest
- Retrieve a layer digest by a selector (explained below)
- (Optional) Prune the cache

Cache entry writes are provided with:

- Layer blob (generates the layer digest, diff ID, and size)
- Optional selector to additionally reference the layer
- Optional metadata blob

Cache entry reads are provided with:

- Layer digest
- Layer diff ID
- Layer size
- Layer blob
- Optional metadata blob

#### Storage engine implementation

There are two types of layers - application layers and base image layers. Base image layers would only need to store their layer data (layer digest and diff ID). Application layers need to store their layer data along with metadata and a custom selector.

In the provided storage implementation, the metadata will be just the last modified time of the layer (the latest modification time for the files that go into the layer). **The selector will be a digest of the layer entries that built the layer.**

For example, the cache directory structure looks like:

```
layers/
  36a2b7401dcddc50a35aeaa81085718b9d5fbce9d607c55a1d79beec2469f9ac/
    326a609681777ee4ca02b1898579c9e07801ef066a629a3c59fa6df6ab42b7aa
    metadata
selectors/
  65de3b72aaf98e4f300ccdf7d64bf9a3b1e23c8c44a1242265f717db1a0877e9
```

The `layers/` directory consists of directories for each layer, named by the layer digest.
Inside each of the layer directories:
- The layer tarball is named by the layer diff ID
- The metadata is stored in the file `metadata`

The `selectors/` directory consists of a file for each selector, named by the selector digest. The selector file contents will be the digest of the layer the selector references. In the example, `selectors/65de3b72aaf98e4f300ccdf7d64bf9a3b1e23c8c44a1242265f717db1a0877e9` will have contents `36a2b7401dcddc50a35aeaa81085718b9d5fbce9d607c55a1d79beec2469f9ac`.

All writes should lock the file they are writing to.

### Analysis

*Note: This analysis assumes that directory traversals are constant, but some filesystems have linear time directory traversal. To account for directory traversals, see [directory sharding](#directory-sharding) below.*

#### Save a cache entry

Each cache entry (layer data, metadata, selector) is stored independently. This solves all of the concurrent metadata write problems due to a single metadata file. Writes are now *constant time* rather than linear in the size of the cache.

#### List the entries in the cache

Listing the entries (by list of layer digests) is a simple call to list the names of the directories in `layers/`. Listing is *linear* in the size of the cache.

#### Retrieve a cache entry by layer digest

Retrieving an entry by layer digest is simply finding the file referenced at `layers/<the layer digest>` (along with associated files). This operation is *constant* in the size of the cache.

#### Retrieve a layer digest by a selector

The higher-level operation is finding a layer that was built by a list of layer entries (the files that built the layer). The operation generates a digest of the layer entries (the selector) and then the storage engine simply finds the file referenced at `selectors/<the selector digest>` (from which the layer digest is retrieved). This operation is *constant* in the size of the cache (and linear in the number of layer entries).

### Directory sharding

Directory traversal may be at worst a linear time operation (such as ext2 and FAT), and at best a logarithmic time operation (such as with the B-tree directory structure of ext3/4, HFS+, NTFS). The linear time directory traversal could potentially be an issue if the cache grows without bound. To address this issue, the contents of a directory can be sharded into a set number of bins to achieve logarithmic time traversal. For example, given a flat directory of files:

```
directory/
  file1
  file2
  ...
  file100000
```

The files can be placed in a select number of bins (say 4 in this case):

```
directory/
  bin1/
    file1
    ...
  bin2/
    file25001
    ...
  bin3/
    file50001
    ...
  bin4/
    file75001
    ...
```

This technique can be applied recursively within each bin as well (with a sentinel to denote recursive termination).

By choosing a constant number of bins, the traversal at each directory is constant time. The total depth of the sharding is logarithmic to the number of files. *The overall traversal time is logarithmic.*

In practice, an effective number of bins may be 256, named by the hex codes `00` through `ff`.

### Locking

All file writes will obtain an exclusive lock on the file and all file reads will obtain a shared lock on the file. However, obtaining locks on the same file during concurrent executions of Jib within the same JVM would result in an `OverlappingFileLockException`. Therefore, the cache cannot rely on `FileLock`s for concurrent I/O control on files.

#### Save a cache entry

Saving a cache entry requires saving the layer blob and metadata to a layer directory under `layers/` and the selector file to the `selectors/` directory. 

The layer directory should be written atomically to avoid reads that may read an incomplete layer directory.

The selector file can be written atomically anytime after the layer directory is fully written.

All atomic writes should be done by first writing to a temporary location (outside of the cache) and then moving to the desired location.

#### List the entries in the cache

Listing the entries in the cache just requires obtaining a list of all the layer directories under `layers/`.

#### Retrieve a cache entry by layer digest

Since layer directories are only moved to their intended location after finishing the atomic write, retrieving by layer digest (via finding the file at the intended location) would always retrieve a completely finished and valid layer directory.

#### Retrieve a layer digest by a selector

Since selectors are only written after the layer directory it selects is completely written, retrieving by selector would always retrieve a completely finished and valid layer directory.
