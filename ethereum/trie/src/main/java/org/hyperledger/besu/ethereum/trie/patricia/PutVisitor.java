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

import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeFactory;
import org.hyperledger.besu.ethereum.trie.NullNode;
import org.hyperledger.besu.ethereum.trie.PathNodeVisitor;

import org.apache.tuweni.bytes.Bytes;

// Visitor的一种，用来在Trie中插入一个新的键值对
public class PutVisitor<V> implements PathNodeVisitor<V> {
  private final NodeFactory<V> nodeFactory;
  private final V value;

  public PutVisitor(final NodeFactory<V> nodeFactory, final V value) {
    this.nodeFactory = nodeFactory;
    this.value = value;
  }

  @Override
  public Node<V> visit(final ExtensionNode<V> extensionNode, final Bytes path) {
    final Bytes extensionPath = extensionNode.getPath();
    // extensionPath和path的共同前缀长度
    final int commonPathLength = extensionPath.commonPrefixLength(path);
    assert commonPathLength < path.size() : "Visiting path doesn't end with a non-matching terminator";
    // 如果相等，说明path和extensionPath完全相同，只需要更新extensionNode的child即可
    if (commonPathLength == extensionPath.size()) {
      // 这里就是更新extensionNode的child, 可能是 Branch，也可能是 Leaf. 这里就看出visitor的作用了，根据不同的Node类型，执行不同的操作
      final Node<V> newChild = extensionNode.getChild().accept(this, path.slice(commonPathLength));
      return extensionNode.replaceChild(newChild);
    }
    // path diverges before the end of the extension - create a new branch
    final byte leafIndex = path.get(commonPathLength); // leaf节点的index
    final Bytes leafPath = path.slice(commonPathLength + 1); // leaf节点的path
    final byte extensionIndex = extensionPath.get(commonPathLength); // extension节点的index
    final Node<V> updatedExtension = extensionNode.replacePath(extensionPath.slice(commonPathLength + 1)); // 更新extensionNode的path
    final Node<V> leaf = nodeFactory.createLeaf(leafPath, value); // 创建leaf节点
    final Node<V> branch = nodeFactory.createBranch(leafIndex, leaf, extensionIndex, updatedExtension); // 根据Extension创建branch节点
    if (commonPathLength > 0) {
      // 如果commonPathLength > 0，那么就需要再创建一个extension节点，来承载commonPath
      return nodeFactory.createExtension(extensionPath.slice(0, commonPathLength), branch);
    } else {
      return branch;
    }
  }

  @Override
  public Node<V> visit(final BranchNode<V> branchNode, final Bytes path) {
    assert path.size() > 0 : "Visiting path doesn't end with a non-matching terminator";
    final byte childIndex = path.get(0);
    if (childIndex == CompactEncoding.LEAF_TERMINATOR) {
      return branchNode.replaceValue(value);
    }
    final Node<V> updatedChild = branchNode.child(childIndex).accept(this, path.slice(1));
    return branchNode.replaceChild(childIndex, updatedChild);
  }

  /**
   * 如果是LeafNode，怎会走这段逻辑
   */
  @Override
  public Node<V> visit(final LeafNode<V> leafNode, final Bytes path) {
    final Bytes leafPath = leafNode.getPath();
    final int commonPathLength = leafPath.commonPrefixLength(path);
    // 如果commonPathLength和path完全相同，那么只需要更新leafNode的value即可
    // Check if the current leaf node should be replaced
    if (commonPathLength == leafPath.size() && commonPathLength == path.size()) {
      return nodeFactory.createLeaf(leafPath, value);
    }

    assert commonPathLength < leafPath.size() && commonPathLength < path.size()
        : "Should not have consumed non-matching terminator";

    // Leaf节点必须进行分叉了
    // The current leaf path must be split to accommodate the new value.
    final byte newLeafIndex = path.get(commonPathLength); // 新的leaf节点的index,如果commonPathLength = 0的话，返回path的第一个字节
    final Bytes newLeafPath = path.slice(commonPathLength + 1); // 新的leaf节点的path, 如果commonPathLength = 0的话，path = path.slice(1..)
    final byte updatedLeafIndex = leafPath.get(commonPathLength); // 更新后的leaf节点的index, 如果commonPathLength = 0的话，返回leafPath的第一个字节
    final Node<V> updatedLeaf = leafNode.replacePath(leafPath.slice(commonPathLength + 1)); // 更新leftNode的path
    // 为path创建新的leaf节点
    final Node<V> leaf = nodeFactory.createLeaf(newLeafPath, value);
    // 创建新的branch节点, 将分叉的两个节点维护起来, 创建branch节点只需要index, 因为branch节点有固定的格式，不需要path
    final Node<V> branch = nodeFactory.createBranch(updatedLeafIndex, updatedLeaf, newLeafIndex, leaf);
    if (commonPathLength > 0) {
      // 如果commonPathLength > 0，那么创建一个extension节点，将branch节点作为child
      return nodeFactory.createExtension(leafPath.slice(0, commonPathLength), branch);
    } else {
      // 如果commonPathLength = 0，那么直接返回branch节点, 说明path和leafPath没有共同前缀
      return branch;
    }
  }

  // NullNode表示当前Trie为空或尚未初始化。这使得在Trie的初始状态下能够安全地进行插入和查找操作
  // 如果是初始化的第一次插入，则返回值是插入的leaf节点
  @Override
  public Node<V> visit(final NullNode<V> nullNode, final Bytes path) {
    return nodeFactory.createLeaf(path, value);
  }
}
