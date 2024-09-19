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
package org.hyperledger.besu.datatypes;

import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes48;

/** This class contains the data for a KZG commitment.
 * 一种基于 KZG (Kate-Zaverucha-Goldberg) 多项式承诺方案的承诺。它允许通过一个简洁的承诺（commitment）来证明和验证多项式评估，而无需披露多项式的全部信息。这种承诺方案广泛用于区块链中的数据验证和扩展性应用，如以太坊的 "数据可用性" 证明机制
 * 可用于
 *  承诺多项式：用户可以提交某个多项式的承诺值（commitment），这可以认为是这个多项式的一个固定点的承诺
 *  评估证明（Proof of Evaluation）：用户可以证明某个特定输入下的多项式评估值是正确的，而无需公开整个多项式
 *  证效率高：验证者可以通过这个承诺值和评估证明来高效验证评估的正确性，而不需要知道多项式的具体形式
 * 特点
 *  承诺的简洁性：多项式承诺通过一个固定大小的承诺值来表达，即使多项式的度数很高，承诺值的大小也是固定的。这使得它在区块链应用中非常高效，尤其是处理大量数据时
 *  评估和验证的简便性：验证者只需要验证一个评估点及其证明，而不需要查看整个多项式。这种特性在分布式系统中能极大减少数据传输和计算量
 *  数据可用性和扩展性：KZGCommitment 可以确保系统中存储的数据是可用且完整的，特别是在 Layer 2 解决方案、分片（sharding）和 rollup 系统中。例如，数据提供者可以承诺某个区块数据的多项式形式，然后提供简洁的评估证明，让验证者确认数据的有效性
 * 典型的应用场景:
 *  以太坊扩展性（EIP-4844）： 在以太坊的扩展提案 EIP-4844（又称 "Proto-Danksharding"）中，KZG 承诺被用来验证 "数据 blobs" 的可用性。这种方案希望在扩展以太坊吞吐量的同时，减少每个节点需要存储的状态数据量。通过 KZGCommitment，节点可以提交关于数据 blob 的承诺，而验证者则可以验证这些 blobs 的可用性，而不需要下载和存储整个数据集
 *  Layer 2 Rollups： 在 Layer 2 解决方案（如 zk-Rollups）中，KZGCommitment 可以用于提交一批交易或状态数据的承诺，而证明者可以提供简洁的证明，表明这些数据是有效的。KZGCommitment 减少了数据传输和计算复杂度，提升了扩展性
 *  分片技术（Sharding）： 在分片架构中，KZGCommitment 可以被用来证明特定分片的数据可用性，防止数据丢失或节点作弊
 * 工作原理
 *  1. 承诺（Commitment）： 用户首先定义一个多项式𝑃(𝑥) 然后使用 KZG 承诺方案生成一个承诺值C，这个承诺值是对多项式的一种简洁表示
 *  2. 评估（Evaluation）： 当需要验证某个输入x_0对应多项式输出 y_0 = P(x_0)时，证明者可以生成一个评估证明 π,来表名 P(x_0) = y_0
 *  3. 验证（Verification）: 验证者收到C, x_0, y_0 和 π 后，可以高效地验证这个多项式评估的正确性，而无需知道多项式的完整形式。这种验证通过椭圆曲线上的双线性配对运算来实现
 *
 * 优势
 *  1. 简洁的承诺值：即使是高阶多项式，承诺值也是固定大小的
 *  2. 高效的验证：通过简短的证明即可验证多项式评估的正确性
 *  3. 应用广泛：适用于数据可用性、状态验证和扩展性场景
 * 局限
 *  1. 受信设置（Trusted Setup）：KZG 承诺方案需要预先的可信设置，确保承诺的安全性和随机性。这可能会带来信任和安全风险
 *  2. 复杂性：虽然验证高效，但生成承诺和证明的过程可能比较复杂，尤其是在处理大量数据时
 * */

public class KZGCommitment {
  final Bytes48 data;

  /**
   * Constructor for a KZG commitment.
   *
   * @param data The data for the KZG commitment.
   */
  public KZGCommitment(final Bytes48 data) {
    this.data = data;
  }

  /**
   * Reads a KZG commitment from the RLP input.
   *
   * @param input The RLP input.
   * @return The KZG commitment.
   */
  public static KZGCommitment readFrom(final RLPInput input) {
    final Bytes48 bytes = input.readBytes48();
    return new KZGCommitment(bytes);
  }

  /**
   * Writes the KZG commitment to the RLP output.
   *
   * @param out The RLP output.
   */
  public void writeTo(final RLPOutput out) {
    out.writeBytes(data);
  }

  /**
   * Gets the data for the KZG commitment.
   *
   * @return The data for the KZG commitment.
   */
  public Bytes48 getData() {
    return data;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    KZGCommitment that = (KZGCommitment) o;
    return Objects.equals(getData(), that.getData());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getData());
  }
}
