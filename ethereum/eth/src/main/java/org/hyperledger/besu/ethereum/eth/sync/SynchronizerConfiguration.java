/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncConfiguration;
import org.hyperledger.besu.services.tasks.CachingTaskCollection;

import java.util.concurrent.TimeUnit;

import com.google.common.collect.Range;
import org.apache.tuweni.units.bigints.UInt256;

public class SynchronizerConfiguration {

    /**
     * 主要用于控制在同步过程中，节点选择的枢轴块（pivot block）与当前区块链头部之间的距离
     * 从而减少了在同步过程中可能遇到的数据重复或处理瓶颈
     */
    public static final int DEFAULT_PIVOT_DISTANCE_FROM_HEAD = 50;

    /**
     * 用于定义在区块链同步过程中执行完整区块验证的频率。这个参数主要用于平衡同步性能和安全性
     * 在同步过程中，节点会从其他节点下载区块数据。完整区块验证指的是对这些下载的区块进行全面的验证，包括验证区块头、区块体、交易和状态转换等所有数据的正确性和一致性
     * 该参数通常设置为一个比例值（例如 0.1 表示 10%），这意味着在同步过程中，大约每 10 个区块中会对 1 个区块进行完整验证，而其余的区块只进行部分或基本验证
     */
    public static final float DEFAULT_FULL_VALIDATION_RATE = .1f;

    /**
     * 同步所需的最小对等节点数量
     * 多个对等节点提供数据，能够相互验证数据的正确性，减少数据被污染的风险。此外，如果一个节点无法提供所需数据，其他节点仍然可以作为备份数据源
     */
    public static final int DEFAULT_SYNC_MINIMUM_PEERS = 5;

    /**
     * 用于指定在同步过程中每个请求获取的世界状态（World State）哈希的数量
     * 如果每次请求的哈希数量太多，一旦发生网络错误或数据丢失，重试的成本就会很高。通过限制请求量，可以减少由于错误而导致的重试代价，提高同步的稳定性
     */
    public static final int DEFAULT_WORLD_STATE_HASH_COUNT_PER_REQUEST = 384;

    /**
     * 在同步过程中，节点能够同时发送的请求数量。这些请求可能包括状态节点（state trie nodes）、账户数据和存储数据等
     * 通过限制并发请求的数量，可以减少网络请求的竞争，提高同步的稳定性
     */
    public static final int DEFAULT_WORLD_STATE_REQUEST_PARALLELISM = 10;

    /**
     * 无进展请求的最大数量: 在以太坊的世界状态（World State）同步过程中，节点向对等节点发送多个数据请求。如果这些请求未能得到有效的响应（例如，未收到数据或收到错误数据），这就被视为“无进展请求”（requests without progress）
     * 当无进展请求的数量超过该阈值时，节点可能会执行重新连接、切换数据源或其他错误处理措施，以恢复正常的同步进度
     */
    public static final int DEFAULT_WORLD_STATE_MAX_REQUESTS_WITHOUT_PROGRESS = 1000;

    /**
     * 设定一个阈值，以防止节点在同步过程中由于处理世界状态数据而出现严重的延迟或阻塞
     */
    public static final long DEFAULT_WORLD_STATE_MIN_MILLIS_BEFORE_STALLING =
            TimeUnit.MINUTES.toMillis(5);

    /**
     * 默认的区块传播范围
     * 节点在传播区块时的一个范围，通常表示节点在同步区块时可以接收或传播的区块的数量或高度差。这有助于优化节点之间的通信和同步过程，使得节点不必处理超出这个范围的区块，从而提高同步效率。
     */
    public static final Range<Long> DEFAULT_BLOCK_PROPAGATION_RANGE = Range.closed(-10L, 30L);

    /**
     * 指定了一个区块高度差的阈值，当节点检测到它与目标区块的高度差达到或超过这个阈值时，节点会重新计算并调整同步目标，以确保它继续朝向最新的区块头部同步。这有助于节点更加智能地调整同步策略，避免在网络条件变化时浪费资源或陷入不必要的重新同步过程
     */
    public static final long DEFAULT_DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_HEIGHT = 200L;

    /**
     * 在区块链网络（特别是像以太坊这样的区块链）中，每个区块都有一个“总难度”值，该值表示从创世区块到当前区块为止，累积的计算难度。总难度是衡量区块链工作量的一个重要指标，因为区块链的最长链（最合法的链）是总难度最大的链
     * 当节点检测到它当前同步的链的总难度与目标链的总难度之差达到或超过这个阈值时，节点会决定调整其同步目标，以确保继续向总难度更高的链进行同步
     */
    public static final UInt256 DEFAULT_DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_TD =
            UInt256.valueOf(1_000_000_000_000_000_000L);

    /**
     * 每次请求区块头的默认数量
     */
    public static final int DEFAULT_DOWNLOADER_HEADER_REQUEST_SIZE = 200;

    /**
     * 指定了在同步过程中允许的检查点超时次数上限。当超时次数达到这个阈值时，节点可能会采取额外的措施，如重新尝试同步、调整同步策略或记录错误，以确保同步过程能够继续进行而不会长时间被阻塞
     */
    public static final int DEFAULT_DOWNLOADER_CHECKPOINT_TIMEOUTS_PERMITTED = 5;

    /**
     * 指定了在同步过程中每个链段的默认大小。链段是区块链中的一部分，通常包含一组相邻的区块，用于同步和验证区块链数据
     */
    public static final int DEFAULT_DOWNLOADER_CHAIN_SEGMENT_SIZE = 200;

    /**
     * 默认的下载器并行度
     */
    public static final int DEFAULT_DOWNLOADER_PARALLELISM = 4;

    /**
     * 默认的事务并行度
     */
    public static final int DEFAULT_TRANSACTIONS_PARALLELISM = 4;

    /**
     * 默认的计算并行度
     */
    public static final int DEFAULT_COMPUTATION_PARALLELISM = 2;

    /**
     * 定义了世界状态任务缓存的默认大小，即可以在缓存中存储的任务数量。这个缓存用于保存与世界状态相关的任务或数据，以便在未来的操作中可以快速访问，减少计算和数据库访问的开销
     */
    public static final int DEFAULT_WORLD_STATE_TASK_CACHE_SIZE =
            CachingTaskCollection.DEFAULT_CACHE_SIZE;
    public static final long DEFAULT_PROPAGATION_MANAGER_GET_BLOCK_TIMEOUT_MILLIS =
            TimeUnit.SECONDS.toMillis(60);

    public static final boolean DEFAULT_CHECKPOINT_POST_MERGE_ENABLED = false;

    // Fast sync config
    private final int syncPivotDistance;
    private final float fastSyncFullValidationRate;
    private final int syncMinimumPeerCount;
    private final int worldStateHashCountPerRequest;
    private final int worldStateRequestParallelism;
    private final int worldStateMaxRequestsWithoutProgress;
    private final int worldStateTaskCacheSize;

    // Snapsync
    private final SnapSyncConfiguration snapSyncConfiguration;

    // Block propagation config
    private final Range<Long> blockPropagationRange;

    // General config
    private final SyncMode syncMode;

    // Near head Checkpoint sync
    private final boolean checkpointPostMergeEnabled;

    // Downloader config
    private final long downloaderChangeTargetThresholdByHeight;
    private final UInt256 downloaderChangeTargetThresholdByTd;
    private final int downloaderHeaderRequestSize;
    private final int downloaderCheckpointRetries;
    private final int downloaderChainSegmentSize;
    private final int downloaderParallelism;
    private final int transactionsParallelism;
    private final int computationParallelism;
    private final int maxTrailingPeers;
    private final long worldStateMinMillisBeforeStalling;
    private final long propagationManagerGetBlockTimeoutMillis;

    private SynchronizerConfiguration(
            final int syncPivotDistance,
            final float fastSyncFullValidationRate,
            final int syncMinimumPeerCount,
            final int worldStateHashCountPerRequest,
            final int worldStateRequestParallelism,
            final int worldStateMaxRequestsWithoutProgress,
            final long worldStateMinMillisBeforeStalling,
            final int worldStateTaskCacheSize,
            final SnapSyncConfiguration snapSyncConfiguration,
            final Range<Long> blockPropagationRange,
            final SyncMode syncMode,
            final long downloaderChangeTargetThresholdByHeight,
            final UInt256 downloaderChangeTargetThresholdByTd,
            final int downloaderHeaderRequestSize,
            final int downloaderCheckpointRetries,
            final int downloaderChainSegmentSize,
            final int downloaderParallelism,
            final int transactionsParallelism,
            final int computationParallelism,
            final int maxTrailingPeers,
            final long propagationManagerGetBlockTimeoutMillis,
            final boolean checkpointPostMergeEnabled) {
        this.syncPivotDistance = syncPivotDistance;
        this.fastSyncFullValidationRate = fastSyncFullValidationRate;
        this.syncMinimumPeerCount = syncMinimumPeerCount;
        this.worldStateHashCountPerRequest = worldStateHashCountPerRequest;
        this.worldStateRequestParallelism = worldStateRequestParallelism;
        this.worldStateMaxRequestsWithoutProgress = worldStateMaxRequestsWithoutProgress;
        this.worldStateMinMillisBeforeStalling = worldStateMinMillisBeforeStalling;
        this.worldStateTaskCacheSize = worldStateTaskCacheSize;
        this.snapSyncConfiguration = snapSyncConfiguration;
        this.blockPropagationRange = blockPropagationRange;
        this.syncMode = syncMode;
        this.downloaderChangeTargetThresholdByHeight = downloaderChangeTargetThresholdByHeight;
        this.downloaderChangeTargetThresholdByTd = downloaderChangeTargetThresholdByTd;
        this.downloaderHeaderRequestSize = downloaderHeaderRequestSize;
        this.downloaderCheckpointRetries = downloaderCheckpointRetries;
        this.downloaderChainSegmentSize = downloaderChainSegmentSize;
        this.downloaderParallelism = downloaderParallelism;
        this.transactionsParallelism = transactionsParallelism;
        this.computationParallelism = computationParallelism;
        this.maxTrailingPeers = maxTrailingPeers;
        this.propagationManagerGetBlockTimeoutMillis = propagationManagerGetBlockTimeoutMillis;
        this.checkpointPostMergeEnabled = checkpointPostMergeEnabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The actual sync mode to be used.
     *
     * @return the sync mode
     */
    public SyncMode getSyncMode() {
        return syncMode;
    }

    public boolean isCheckpointPostMergeEnabled() {
        return checkpointPostMergeEnabled;
    }

    /**
     * All the configuration related to snapsync
     *
     * @return snapsync configuration
     */
    public SnapSyncConfiguration getSnapSyncConfiguration() {
        return snapSyncConfiguration;
    }

    /**
     * The range of block numbers (relative to the current chain head and the best network block) that
     * are considered appropriate to import as new blocks are announced on the network.
     *
     * @return the range of blocks considered valid to import from the network, relative to the
     * current chain head.
     */
    public Range<Long> getBlockPropagationRange() {
        return blockPropagationRange;
    }

    /**
     * The distance from the chain head at which we should switch from fast, snap, or checkpoint sync
     * to full sync.
     *
     * @return distance from the chain head at which we should switch from fast, snap or checkpoint
     * sync to full sync.
     */
    public int getSyncPivotDistance() {
        return syncPivotDistance;
    }

    public long getDownloaderChangeTargetThresholdByHeight() {
        return downloaderChangeTargetThresholdByHeight;
    }

    public UInt256 getDownloaderChangeTargetThresholdByTd() {
        return downloaderChangeTargetThresholdByTd;
    }

    public int getDownloaderHeaderRequestSize() {
        return downloaderHeaderRequestSize;
    }

    public int getDownloaderCheckpointRetries() {
        return downloaderCheckpointRetries;
    }

    public int getDownloaderChainSegmentSize() {
        return downloaderChainSegmentSize;
    }

    public int getDownloaderParallelism() {
        return downloaderParallelism;
    }

    public int getTransactionsParallelism() {
        return transactionsParallelism;
    }

    public int getComputationParallelism() {
        return computationParallelism;
    }

    /**
     * The rate at which blocks should be fully validated during fast sync. At a rate of 1f, all
     * blocks are fully validated. At rates less than 1f, a subset of blocks will undergo light-weight
     * validation.
     *
     * @return rate at which blocks should be fully validated during fast sync.
     */
    public float getFastSyncFullValidationRate() {
        return fastSyncFullValidationRate;
    }

    public int getSyncMinimumPeerCount() {
        return syncMinimumPeerCount;
    }

    public int getWorldStateHashCountPerRequest() {
        return worldStateHashCountPerRequest;
    }

    public int getWorldStateRequestParallelism() {
        return worldStateRequestParallelism;
    }

    public int getWorldStateMaxRequestsWithoutProgress() {
        return worldStateMaxRequestsWithoutProgress;
    }

    public long getWorldStateMinMillisBeforeStalling() {
        return worldStateMinMillisBeforeStalling;
    }

    public int getWorldStateTaskCacheSize() {
        return worldStateTaskCacheSize;
    }

    public int getMaxTrailingPeers() {
        return maxTrailingPeers;
    }

    public long getPropagationManagerGetBlockTimeoutMillis() {
        return propagationManagerGetBlockTimeoutMillis;
    }

    public static class Builder {
        private SyncMode syncMode = SyncMode.FULL;
        private int syncMinimumPeerCount = DEFAULT_SYNC_MINIMUM_PEERS;
        private int maxTrailingPeers = Integer.MAX_VALUE;
        private Range<Long> blockPropagationRange = DEFAULT_BLOCK_PROPAGATION_RANGE;
        private long downloaderChangeTargetThresholdByHeight =
                DEFAULT_DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_HEIGHT;
        private UInt256 downloaderChangeTargetThresholdByTd =
                DEFAULT_DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_TD;
        private int downloaderHeaderRequestSize = DEFAULT_DOWNLOADER_HEADER_REQUEST_SIZE;
        private int downloaderCheckpointRetries = DEFAULT_DOWNLOADER_CHECKPOINT_TIMEOUTS_PERMITTED;
        private SnapSyncConfiguration snapSyncConfiguration = SnapSyncConfiguration.getDefault();
        private int downloaderChainSegmentSize = DEFAULT_DOWNLOADER_CHAIN_SEGMENT_SIZE;
        private int downloaderParallelism = DEFAULT_DOWNLOADER_PARALLELISM;
        private int transactionsParallelism = DEFAULT_TRANSACTIONS_PARALLELISM;
        private int computationParallelism = DEFAULT_COMPUTATION_PARALLELISM;
        private int syncPivotDistance = DEFAULT_PIVOT_DISTANCE_FROM_HEAD;
        private float fastSyncFullValidationRate = DEFAULT_FULL_VALIDATION_RATE;
        private int worldStateHashCountPerRequest = DEFAULT_WORLD_STATE_HASH_COUNT_PER_REQUEST;
        private int worldStateRequestParallelism = DEFAULT_WORLD_STATE_REQUEST_PARALLELISM;
        private int worldStateMaxRequestsWithoutProgress =
                DEFAULT_WORLD_STATE_MAX_REQUESTS_WITHOUT_PROGRESS;
        private long worldStateMinMillisBeforeStalling = DEFAULT_WORLD_STATE_MIN_MILLIS_BEFORE_STALLING;
        private int worldStateTaskCacheSize = DEFAULT_WORLD_STATE_TASK_CACHE_SIZE;

        private long propagationManagerGetBlockTimeoutMillis =
                DEFAULT_PROPAGATION_MANAGER_GET_BLOCK_TIMEOUT_MILLIS;
        private boolean checkpointPostMergeEnabled = DEFAULT_CHECKPOINT_POST_MERGE_ENABLED;

        public Builder syncPivotDistance(final int distance) {
            syncPivotDistance = distance;
            return this;
        }

        public Builder fastSyncFullValidationRate(final float rate) {
            this.fastSyncFullValidationRate = rate;
            return this;
        }

        public Builder snapSyncConfiguration(final SnapSyncConfiguration snapSyncConfiguration) {
            this.snapSyncConfiguration = snapSyncConfiguration;
            return this;
        }

        public Builder syncMode(final SyncMode mode) {
            this.syncMode = mode;
            return this;
        }

        public Builder blockPropagationRange(final Range<Long> blockPropagationRange) {
            checkNotNull(blockPropagationRange);
            this.blockPropagationRange = blockPropagationRange;
            return this;
        }

        public Builder downloaderChangeTargetThresholdByHeight(
                final long downloaderChangeTargetThresholdByHeight) {
            this.downloaderChangeTargetThresholdByHeight = downloaderChangeTargetThresholdByHeight;
            return this;
        }

        public Builder downloaderChangeTargetThresholdByTd(
                final UInt256 downloaderChangeTargetThresholdByTd) {
            this.downloaderChangeTargetThresholdByTd = downloaderChangeTargetThresholdByTd;
            return this;
        }

        public Builder downloaderHeadersRequestSize(final int downloaderHeaderRequestSize) {
            this.downloaderHeaderRequestSize = downloaderHeaderRequestSize;
            return this;
        }

        public Builder downloaderCheckpointRetries(final int downloaderCheckpointRetries) {
            this.downloaderCheckpointRetries = downloaderCheckpointRetries;
            return this;
        }

        public Builder downloaderChainSegmentSize(final int downloaderChainSegmentSize) {
            this.downloaderChainSegmentSize = downloaderChainSegmentSize;
            return this;
        }

        public Builder blockPropagationRange(final long min, final long max) {
            checkArgument(min < max, "Invalid range: min must be less than max.");
            blockPropagationRange = Range.closed(min, max);
            return this;
        }

        public Builder downloaderParallelism(final int downloaderParallelism) {
            this.downloaderParallelism = downloaderParallelism;
            return this;
        }

        public Builder transactionsParallelism(final int transactionsParallelism) {
            this.transactionsParallelism = transactionsParallelism;
            return this;
        }

        public Builder computationParallelism(final int computationParallelism) {
            this.computationParallelism = computationParallelism;
            return this;
        }

        public Builder syncMinimumPeerCount(final int syncMinimumPeerCount) {
            this.syncMinimumPeerCount = syncMinimumPeerCount;
            return this;
        }

        public Builder worldStateHashCountPerRequest(final int worldStateHashCountPerRequest) {
            this.worldStateHashCountPerRequest = worldStateHashCountPerRequest;
            return this;
        }

        public Builder worldStateRequestParallelism(final int worldStateRequestParallelism) {
            this.worldStateRequestParallelism = worldStateRequestParallelism;
            return this;
        }

        public Builder worldStateMaxRequestsWithoutProgress(
                final int worldStateMaxRequestsWithoutProgress) {
            this.worldStateMaxRequestsWithoutProgress = worldStateMaxRequestsWithoutProgress;
            return this;
        }

        public Builder worldStateMinMillisBeforeStalling(final long worldStateMinMillisBeforeStalling) {
            this.worldStateMinMillisBeforeStalling = worldStateMinMillisBeforeStalling;
            return this;
        }

        public Builder worldStateTaskCacheSize(final int worldStateTaskCacheSize) {
            this.worldStateTaskCacheSize = worldStateTaskCacheSize;
            return this;
        }

        public Builder maxTrailingPeers(final int maxTailingPeers) {
            this.maxTrailingPeers = maxTailingPeers;
            return this;
        }

        public Builder propagationManagerGetBlockTimeoutMillis(
                final long propagationManagerGetBlockTimeoutMillis) {
            this.propagationManagerGetBlockTimeoutMillis = propagationManagerGetBlockTimeoutMillis;
            return this;
        }

        public Builder checkpointPostMergeEnabled(final boolean checkpointPostMergeEnabled) {
            this.checkpointPostMergeEnabled = checkpointPostMergeEnabled;
            return this;
        }

        public SynchronizerConfiguration build() {
            return new SynchronizerConfiguration(
                    syncPivotDistance,
                    fastSyncFullValidationRate,
                    syncMinimumPeerCount,
                    worldStateHashCountPerRequest,
                    worldStateRequestParallelism,
                    worldStateMaxRequestsWithoutProgress,
                    worldStateMinMillisBeforeStalling,
                    worldStateTaskCacheSize,
                    snapSyncConfiguration,
                    blockPropagationRange,
                    syncMode,
                    downloaderChangeTargetThresholdByHeight,
                    downloaderChangeTargetThresholdByTd,
                    downloaderHeaderRequestSize,
                    downloaderCheckpointRetries,
                    downloaderChainSegmentSize,
                    downloaderParallelism,
                    transactionsParallelism,
                    computationParallelism,
                    maxTrailingPeers,
                    propagationManagerGetBlockTimeoutMillis,
                    checkpointPostMergeEnabled);
        }
    }
}
