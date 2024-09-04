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
package org.hyperledger.besu.ethereum.p2p.peers;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.PeerInfo;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

// DefaultLocalNode 是 Hyperledger Besu 的一个核心类，用于管理本地节点的状态、属性和与其他节点的网络交互。它负责节点发现、连接管理和节点状态的更新，是保证以太坊网络节点正常运作的重要组成部分。
class DefaultLocalNode implements MutableLocalNode {

  private final AtomicBoolean isReady = new AtomicBoolean(false);
  private final String clientId;
  private final int p2pVersion;
  private final List<Capability> supportedCapabilities;
  private volatile Optional<PeerInfo> peerInfo = Optional.empty();
  private volatile Optional<Peer> peer = Optional.empty();

  private DefaultLocalNode(
      final String clientId, final int p2pVersion, final List<Capability> supportedCapabilities) {
    this.clientId = clientId;
    this.p2pVersion = p2pVersion;
    this.supportedCapabilities = supportedCapabilities;
  }

  public static DefaultLocalNode create(
      final String clientId, final int p2pVersion, final List<Capability> supportedCapabilities) {
    return new DefaultLocalNode(clientId, p2pVersion, supportedCapabilities);
  }

  @Override
  public void setEnode(final EnodeURL enode) throws NodeAlreadySetException {
    if (peer.isPresent()) {
      throw new NodeAlreadySetException("Attempt to set already initialized local node");
    }
    this.peerInfo =
        Optional.of(
            new PeerInfo(
                p2pVersion,
                clientId,
                supportedCapabilities,
                enode.getListeningPortOrZero(),
                enode.getNodeId()));
    this.peer = Optional.of(DefaultPeer.fromEnodeURL(enode));
    isReady.set(true);
  }

  @Override
  public PeerInfo getPeerInfo() throws NodeNotReadyException {
    if (!peerInfo.isPresent()) {
      throw new NodeNotReadyException(
          "Attempt to access local peer info before local node is ready.");
    }
    return peerInfo.get();
  }

  @Override
  public Peer getPeer() throws NodeNotReadyException {
    if (!peer.isPresent()) {
      throw new NodeNotReadyException(
          "Attempt to access local peer representation before local node is ready.");
    }
    return peer.get();
  }

  @Override
  public boolean isReady() {
    return isReady.get();
  }
}
