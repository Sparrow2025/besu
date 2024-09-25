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
import org.hyperledger.besu.ethereum.trie.NullNode;
import org.hyperledger.besu.ethereum.trie.PathNodeVisitor;

import org.apache.tuweni.bytes.Bytes;

public class RemoveVisitor<V> implements PathNodeVisitor<V> {
  private final Node<V> NULL_NODE_RESULT = NullNode.instance();

  private final boolean allowFlatten;

  public RemoveVisitor() {
    allowFlatten = true;
  }

  public RemoveVisitor(final boolean allowFlatten) {
    this.allowFlatten = allowFlatten;
  }

  @Override
  public Node<V> visit(final ExtensionNode<V> extensionNode, final Bytes path) {
    final Bytes extensionPath = extensionNode.getPath();
    final int commonPathLength = extensionPath.commonPrefixLength(path);
    assert commonPathLength < path.size()
        : "Visiting path doesn't end with a non-matching terminator";
    // 如果commonPath和extensionPath长度相等，说明当前extensionNode的child需要更改(可能不是下一级更改，可能是在很多层级之后的修改)
    if (commonPathLength == extensionPath.size()) {
      final Node<V> newChild = extensionNode.getChild().accept(this, path.slice(commonPathLength));
      return extensionNode.replaceChild(newChild);
    }

    // path diverges before the end of the extension, so it cannot match

    return extensionNode;
  }

  @Override
  public Node<V> visit(final BranchNode<V> branchNode, final Bytes path) {
    assert path.size() > 0 : "Visiting path doesn't end with a non-matching terminator";

    final byte childIndex = path.get(0);
    if (childIndex == CompactEncoding.LEAF_TERMINATOR) {
      return branchNode.removeValue();
    }
    final Node<V> updatedChild = branchNode.child(childIndex).accept(this, path.slice(1));
    // 如果更新后的child为NULL_NODE_RESULT，说明该child节点已经被删除，需要将其从BranchNode中删除
    return branchNode.replaceChild(childIndex, updatedChild, allowFlatten);
  }

  /**
   * 不需要对LeafNode节点做太多处理，如果匹配到只需要返回NULL_NODE_RESULT即可，在上层的BranchNode中会将指向其的指针删除
   */
  @Override
  public Node<V> visit(final LeafNode<V> leafNode, final Bytes path) {
    final Bytes leafPath = leafNode.getPath();
    final int commonPathLength = leafPath.commonPrefixLength(path);
    return (commonPathLength == leafPath.size()) ? NULL_NODE_RESULT : leafNode;
  }

  @Override
  public Node<V> visit(final NullNode<V> nullNode, final Bytes path) {
    return NULL_NODE_RESULT;
  }
}
