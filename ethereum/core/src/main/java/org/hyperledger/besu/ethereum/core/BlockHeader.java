/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.Objects;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** A mined Ethereum block header. */
public class BlockHeader extends SealableBlockHeader
    implements org.hyperledger.besu.plugin.data.BlockHeader {

  public static final int MAX_EXTRA_DATA_BYTES = 32;

  public static final long GENESIS_BLOCK_NUMBER = 0L;

  private final long nonce;

  private final Supplier<Hash> hash;

  private final Supplier<ParsedExtraData> parsedExtraData;

  public BlockHeader(
      final Hash parentHash, // 上一个区块的hash
      final Hash ommersHash, // 叔块的hash
      final Address coinbase, // 矿工地址
      final Hash stateRoot, // 状态根
      final Hash transactionsRoot, // 交易根
      final Hash receiptsRoot, // 收据根
      final LogsBloomFilter logsBloom, // 日志布隆过滤器
      final Difficulty difficulty, // 当前区块的难度
      final long number, // 区块高度
      final long gasLimit, // gas限制
      final long gasUsed, // gas使用量
      final long timestamp, // 时间戳
      final Bytes extraData, // 额外数据
      final Wei baseFee, // baseFee
      final Bytes32 mixHashOrPrevRandao, // mixHash帮助计算区块的计算工作量是否有效, prevRandao是用于 PoS 网络中提供随机性来源的一个重要部分
      final long nonce, // PoW中nonce是矿工为了满足网络的难度要求而必须调整的数字 PoS中nonce被弃用,转而使用prevRandao来提供系统随机性
      final Hash withdrawalsRoot, // 用于管理和验证提款（withdrawals）相关的数据
      final Long blobGasUsed, // 用于存储当前区块中存储和处理数据 blob（大型数据块）所消耗的 Gas 数量
      final BlobGas excessBlobGas, // 表示当前区块链状态下 Blob Gas 超过目标数量的字段
      final Bytes32 parentBeaconBlockRoot, // 用于指向当前区块的父级信标区块的哈希值
      final Hash requestsRoot, // 存储和处理执行层（Execution Layer, EL）中的请求
      final BlockHeaderFunctions blockHeaderFunctions) {
    super(
        parentHash,
        ommersHash,
        coinbase,
        stateRoot,
        transactionsRoot,
        receiptsRoot,
        logsBloom,
        difficulty,
        number,
        gasLimit,
        gasUsed,
        timestamp,
        extraData,
        baseFee,
        mixHashOrPrevRandao,
        withdrawalsRoot,
        blobGasUsed,
        excessBlobGas,
        parentBeaconBlockRoot,
        requestsRoot);
    this.nonce = nonce;
    this.hash = Suppliers.memoize(() -> blockHeaderFunctions.hash(this));
    this.parsedExtraData = Suppliers.memoize(() -> blockHeaderFunctions.parseExtraData(this));
  }

  /**
   * Returns the block mixed hash.
   *
   * @return the block mixed hash
   */
  @Override
  public Hash getMixHash() {
    return Hash.wrap(mixHashOrPrevRandao);
  }

  /**
   * Returns the block nonce.
   *
   * @return the block nonce
   */
  @Override
  public long getNonce() {
    return nonce;
  }

  /**
   * Returns the block extra data field, as parsed by the {@link BlockHeaderFunctions}.
   *
   * @return the block extra data field
   */
  public ParsedExtraData getParsedExtraData() {
    return parsedExtraData.get();
  }

  /**
   * Returns the block header hash.
   *
   * @return the block header hash
   */
  public Hash getHash() {
    return hash.get();
  }

  @Override
  public Hash getBlockHash() {
    return hash.get();
  }

  /**
   * Write an RLP representation.
   *
   * @param out The RLP output to write to
   */
  public void writeTo(final RLPOutput out) {
    out.startList();

    out.writeBytes(parentHash);
    out.writeBytes(ommersHash);
    out.writeBytes(coinbase);
    out.writeBytes(stateRoot);
    out.writeBytes(transactionsRoot);
    out.writeBytes(receiptsRoot);
    out.writeBytes(logsBloom);
    out.writeUInt256Scalar(difficulty);
    out.writeLongScalar(number);
    out.writeLongScalar(gasLimit);
    out.writeLongScalar(gasUsed);
    out.writeLongScalar(timestamp);
    out.writeBytes(extraData);
    out.writeBytes(mixHashOrPrevRandao);
    out.writeLong(nonce);
    do {
      if (baseFee == null) break;
      out.writeUInt256Scalar(baseFee);

      if (withdrawalsRoot == null) break;
      out.writeBytes(withdrawalsRoot);

      if (excessBlobGas == null || blobGasUsed == null) break;
      out.writeLongScalar(blobGasUsed);
      out.writeUInt64Scalar(excessBlobGas);

      if (parentBeaconBlockRoot == null) break;
      out.writeBytes(parentBeaconBlockRoot);

      if (requestsRoot == null) break;
      out.writeBytes(requestsRoot);
    } while (false);
    out.endList();
  }

  public static BlockHeader readFrom(
      final RLPInput input, final BlockHeaderFunctions blockHeaderFunctions) {
    input.enterList();
    // 上个区块的hash
    final Hash parentHash = Hash.wrap(input.readBytes32());
    // 叔块的hash: 指的是那些在相同区块高度被开采出来但没有成为主链一部分的区块
    // 1. 叔块机制通过奖励挖出叔块的矿工，来鼓励网络参与者更快地传播和验证新区块。这样做能有效提高全网的去中心化程度和安全性，因为即使某个矿工未能成功将区块加入主链，他的努力仍然被部分认可和奖励
    // 2. 叔块机制容许一些“迟到”的区块仍然有价值，它们可以被引用作为叔块，这样在网络中区块传播延迟引起的分叉现象不至于浪费所有资源，从而提高整体网络的容错性
    // 计算过程：叔块的hash = 叔块1的hash + 叔块2的hash + ... + 叔块n的hash 将这些叔块的头部进行哈希计算，构建一棵默克尔树，然后计算出这棵树的根哈希，即为 ommersHash
    final Hash ommersHash = Hash.wrap(input.readBytes32());
    // 矿工地址
    final Address coinbase = Address.readFrom(input);
    // 状态根
    final Hash stateRoot = Hash.wrap(input.readBytes32());
    // 交易根
    final Hash transactionsRoot = Hash.wrap(input.readBytes32());
    // 收据根
    final Hash receiptsRoot = Hash.wrap(input.readBytes32());
    // 日志布隆过滤器
    final LogsBloomFilter logsBloom = LogsBloomFilter.readFrom(input);
    // 当前区块的难度，是一个256位的无符号整数
    final Difficulty difficulty = Difficulty.of(input.readUInt256Scalar());
    // 区块高度
    final long number = input.readLongScalar();
    // gas限制
    final long gasLimit = input.readLongScalar();
    // gas使用量
    final long gasUsed = input.readLongScalar();
    // 时间戳
    final long timestamp = input.readLongScalar();
    // 额外数据
    final Bytes extraData = input.readBytes();
    // 1. 在以太坊的 PoW 共识算法（Ethash）中，mixHash 用于确保矿工在挖矿过程中进行了足够的工作。具体来说，mixHash 是挖矿过程中生成的一个中间值，它是矿工通过不断哈希和查找特定的 nonce 值而计算出的结果。为了找到一个有效的区块，mixHash 和区块哈希都需要满足一定的难度要求
    // 2. 用于存储上一轮（epoch）验证者随机数的字段。它是用于在以太坊 PoS 网络中提供随机性来源的一个重要部分。随机性在 PoS 中非常关键，因为它用于选举验证者、安排验证者职责、分配验证任务等
    final Bytes32 mixHashOrPrevRandao = input.readBytes32();
    // nonce 是矿工在挖矿过程中反复尝试的一个值。矿工会不断改变 nonce，对区块头进行哈希运算，直到找到一个满足当前难度要求的哈希值。这个过程确保了矿工进行了足够的工作量证明（PoW）
    final long nonce = input.readLong();
    // 在以太坊区块头中，baseFee 是一个新的字段，它在 以太坊伦敦升级（EIP-1559） 中被引入，用于改进交易费用机制。baseFee 旨在通过一个动态调整的基础费用来优化交易费的预测和收取方式，以实现更稳定的用户体验
    // 作用
    // 1. baseFee 通过区块头中的字段指定，它根据网络需求动态调整。在网络拥堵时，baseFee 会自动增加；在网络空闲时，baseFee 会自动减少。这使得用户可以更精确地预测和支付合适的交易费用
    // 2. EIP-1559 引入的 baseFee 机制有助于减少交易费用的波动性。传统的费用机制依赖于用户自行设置的交易费用，这导致费用的波动性较大。而 baseFee 提供了一种自动调整的基础费用标准
    // 3. 在 EIP-1559 之后，baseFee 不会直接支付给矿工，而是会被销毁（从流通供应中移除）。这引入了一种新的通货紧缩机制，即随着交易的增加，一部分以太币将被销毁，减少了总供应量，从而潜在地增加以太币的稀缺性
    // 计算
    // 1. 如果区块的 Gas 使用量高于目标（目标是每个区块的 Gas 使用量的上限的一半），则 baseFee 会增加
    // 2. 如果区块的 Gas 使用量低于目标，baseFee 会减少
    // 每个新区块的 baseFee 调整上限为前一个区块 baseFee 的 12.5%。这种机制确保了每个区块的 baseFee 调整是渐进的，防止了突然的费用剧增或减少
    final Wei baseFee = !input.isEndOfCurrentList() ? Wei.of(input.readUInt256Scalar()) : null;
    // withdrawalHashRoot 确实是一个可选的字段，它与以太坊从工作量证明（Proof of Work, PoW）过渡到权益证明（Proof of Stake, PoS）的过程有关，特别是在以太坊 2.0 升级后的共识机制中，用于管理和验证提款（withdrawals）相关的数据
    // 用于存储关于提款操作的默克尔根（Merkle Root）。在以太坊 2.0 的 PoS 共识机制下，这个字段用来记录提款队列的哈希根，以确保提款数据的完整性和一致性
    // 因为它只在有提款操作需要记录时才被使用。如果当前区块中没有任何提款操作，则这个字段可能为空，或者在 PoS 共识模式下被省略
    final Hash withdrawalHashRoot =
        !(input.isEndOfCurrentList() || input.isZeroLengthString())
            ? Hash.wrap(input.readBytes32())
            : null;
    // 用于表示当前区块中存储和处理数据 blob（大型数据块）所消耗的 Gas 数量。它记录了区块中用来存储这些大数据块（blobs）的总 Gas 用量
    // 大数据块（blob）通常用于扩展以太坊的可用性层，特别是在执行 EIP-4844 提案后的情况下。在这个提案中，数据可用性变得至关重要，因为它需要处理更大量的交易数据，blobGasUsed 有助于计算存储这些数据所需的成本
    final Long blobGasUsed = !input.isEndOfCurrentList() ? input.readLongScalar() : null;
    // 表示当前区块链状态下 Blob Gas 超过目标数量的字段。它反映了以太坊网络处理大数据块所需的 Gas 使用是否超过了预设目标
    // 如果 excessBlobGas 的值较高，这意味着网络需求超过了预设的容量目标，可能会导致与 Blob 存储相关的费用上升。这种费用的上升有助于通过市场机制自动减少对 Blob 资源的需求
    // excessBlobGas 是通过前一个区块的 excessBlobGas 和当前区块的 blobGasUsed 与目标值之间的差异来计算的
    final BlobGas excessBlobGas =
        !input.isEndOfCurrentList() ? BlobGas.of(input.readUInt64Scalar()) : null;
    // parentBeaconBlockRoot 是一个区块头字段，用于指向当前区块的父级信标区块的哈希值
    // 如果一个恶意攻击者试图插入伪造区块或创建分叉链，parentBeaconBlockRoot 提供的链接机制使得这种攻击变得更加困难。任何伪造或无效的区块都会因为无法正确链接到现有的区块链而被拒绝
    final Bytes32 parentBeaconBlockRoot = !input.isEndOfCurrentList() ? input.readBytes32() : null;
    // requestsRoot 使用了 Merkle 树结构来表示区块中的请求集合。这种结构允许网络中的参与者快速验证一个请求是否包含在区块中，而无需下载和存储整个请求列表
    // requestsRoot 在确保数据可用性方面起到了关键作用。对于轻客户端或带宽受限的节点来说，它们可以仅依赖于 requestsRoot 和少量的 Merkle 分支来验证特定请求是否在区块中，而无需下载整个区块数据
    // 工作原理
    // 1. 当一个区块包含多个请求（如交易、数据存储请求等）时，这些请求会被组织成一棵 Merkle 树。每个叶节点代表一个具体的请求，其值是该请求的哈希值
    // 2. 要验证某个请求是否在区块中，节点只需要知道 requestsRoot 和请求的 Merkle 分支（从请求到根节点的路径）
    // 3. 在轻客户端或带宽受限的环境下，节点可以利用 requestsRoot 及其 Merkle 分支来快速验证请求，而不需要下载整个区块的数据
    final Hash requestsRoot = !input.isEndOfCurrentList() ? Hash.wrap(input.readBytes32()) : null;
    input.leaveList();
    return new BlockHeader(
        parentHash,
        ommersHash,
        coinbase,
        stateRoot,
        transactionsRoot,
        receiptsRoot,
        logsBloom,
        difficulty,
        number,
        gasLimit,
        gasUsed,
        timestamp,
        extraData,
        baseFee,
        mixHashOrPrevRandao,
        nonce,
        withdrawalHashRoot,
        blobGasUsed,
        excessBlobGas,
        parentBeaconBlockRoot,
        requestsRoot,
        blockHeaderFunctions);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof BlockHeader other)) {
      return false;
    }
    return getHash().equals(other.getHash());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getHash());
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("BlockHeader{");
    sb.append("number=").append(number).append(", ");
    sb.append("hash=").append(getHash()).append(", ");
    sb.append("parentHash=").append(parentHash).append(", ");
    sb.append("ommersHash=").append(ommersHash).append(", ");
    sb.append("coinbase=").append(coinbase).append(", ");
    sb.append("stateRoot=").append(stateRoot).append(", ");
    sb.append("transactionsRoot=").append(transactionsRoot).append(", ");
    sb.append("receiptsRoot=").append(receiptsRoot).append(", ");
    sb.append("logsBloom=").append(logsBloom).append(", ");
    sb.append("difficulty=").append(difficulty).append(", ");
    sb.append("gasLimit=").append(gasLimit).append(", ");
    sb.append("gasUsed=").append(gasUsed).append(", ");
    sb.append("timestamp=").append(timestamp).append(", ");
    sb.append("extraData=").append(extraData).append(", ");
    sb.append("baseFee=").append(baseFee).append(", ");
    sb.append("mixHashOrPrevRandao=").append(mixHashOrPrevRandao).append(", ");
    sb.append("nonce=").append(nonce).append(", ");
    if (withdrawalsRoot != null) {
      sb.append("withdrawalsRoot=").append(withdrawalsRoot).append(", ");
    }
    if (blobGasUsed != null && excessBlobGas != null) {
      sb.append("blobGasUsed=").append(blobGasUsed).append(", ");
      sb.append("excessBlobGas=").append(excessBlobGas).append(", ");
    }
    if (parentBeaconBlockRoot != null) {
      sb.append("parentBeaconBlockRoot=").append(parentBeaconBlockRoot).append(", ");
    }
    if (requestsRoot != null) {
      sb.append("requestsRoot=").append(requestsRoot);
    }
    return sb.append("}").toString();
  }

  public static org.hyperledger.besu.ethereum.core.BlockHeader convertPluginBlockHeader(
      final org.hyperledger.besu.plugin.data.BlockHeader pluginBlockHeader,
      final BlockHeaderFunctions blockHeaderFunctions) {
    return new org.hyperledger.besu.ethereum.core.BlockHeader(
        Hash.fromHexString(pluginBlockHeader.getParentHash().toHexString()),
        Hash.fromHexString(pluginBlockHeader.getOmmersHash().toHexString()),
        Address.fromHexString(pluginBlockHeader.getCoinbase().toHexString()),
        Hash.fromHexString(pluginBlockHeader.getStateRoot().toHexString()),
        Hash.fromHexString(pluginBlockHeader.getTransactionsRoot().toHexString()),
        Hash.fromHexString(pluginBlockHeader.getReceiptsRoot().toHexString()),
        LogsBloomFilter.fromHexString(pluginBlockHeader.getLogsBloom().toHexString()),
        Difficulty.fromHexString(pluginBlockHeader.getDifficulty().toHexString()),
        pluginBlockHeader.getNumber(),
        pluginBlockHeader.getGasLimit(),
        pluginBlockHeader.getGasUsed(),
        pluginBlockHeader.getTimestamp(),
        pluginBlockHeader.getExtraData(),
        pluginBlockHeader.getBaseFee().map(Wei::fromQuantity).orElse(null),
        pluginBlockHeader.getPrevRandao().orElse(null),
        pluginBlockHeader.getNonce(),
        pluginBlockHeader
            .getWithdrawalsRoot()
            .map(h -> Hash.fromHexString(h.toHexString()))
            .orElse(null),
        pluginBlockHeader.getBlobGasUsed().map(Long::longValue).orElse(null),
        pluginBlockHeader.getExcessBlobGas().map(BlobGas.class::cast).orElse(null),
        pluginBlockHeader.getParentBeaconBlockRoot().orElse(null),
        pluginBlockHeader
            .getRequestsRoot()
            .map(h -> Hash.fromHexString(h.toHexString()))
            .orElse(null),
        blockHeaderFunctions);
  }

  @Override
  public String toLogString() {
    return getNumber() + " (" + getHash() + ")";
  }
}
