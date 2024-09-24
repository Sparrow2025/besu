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
package org.hyperledger.besu.ethereum.trie.patricia;

import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeFactory;
import org.hyperledger.besu.ethereum.trie.NullNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;

/**
 * 提供一种创建Trie节点的标准方式。它通常负责实例化不同类型的Trie节点（如LeafNode、ExtensionNode、BranchNode等），并可能包含以下功能
 * 1. 根据输入的参数（如键值对、子节点等），创建相应类型的节点。这使得节点的创建过程集中化和规范化
 * 2. 封装节点创建逻辑，确保在创建过程中遵循特定的类型约束，减少代码重复
 * 3. 如果将来需要扩展或修改节点类型的创建逻辑，只需修改DefaultNodeFactory，而不必在多个地方进行更改
 * 4. ：通过集中管理节点的创建，可以更容易地进行单元测试和替换节点实现
 */
public class DefaultNodeFactory<V> implements NodeFactory<V> {
  @SuppressWarnings("rawtypes")
  private static final Node NULL_NODE = NullNode.instance();

  // 用于表示节点的子节点数量限制
  private static final int NB_CHILD = 16;

  private final Function<V, Bytes> valueSerializer;

  public DefaultNodeFactory(final Function<V, Bytes> valueSerializer) {
    this.valueSerializer = valueSerializer;
  }

  @Override
  public Node<V> createExtension(final Bytes path, final Node<V> child) {
    return new ExtensionNode<>(path, child, this);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Node<V> createBranch(
      final byte leftIndex, final Node<V> left, final byte rightIndex, final Node<V> right) {
    assert (leftIndex <= NB_CHILD);
    assert (rightIndex <= NB_CHILD);
    assert (leftIndex != rightIndex);

    final ArrayList<Node<V>> children = new ArrayList<>(Collections.nCopies(NB_CHILD, (Node<V>) NULL_NODE));
    if (leftIndex == NB_CHILD) {
      // 如果左节点的索引为16, 那么只需要set right, left的值可以直接存到branch节点中
      children.set(rightIndex, right);
      return createBranch(children, left.getValue());
    } else if (rightIndex == NB_CHILD) {
      children.set(leftIndex, left);
      return createBranch(children, right.getValue());
    } else {
      // 如果左右节点的索引都不为16, 那么需要分别set left和right
      children.set(leftIndex, left);
      children.set(rightIndex, right);
      return createBranch(children, Optional.empty());
    }
  }

  @Override
  public Node<V> createBranch(final List<Node<V>> children, final Optional<V> value) {
    return new BranchNode<>(children, value, this, valueSerializer);
  }

  @Override
  public Node<V> createLeaf(final Bytes path, final V value) {
    return new LeafNode<>(path, value, this, valueSerializer);
  }
}
