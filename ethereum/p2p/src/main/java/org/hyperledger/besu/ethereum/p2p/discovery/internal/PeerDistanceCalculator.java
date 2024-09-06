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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;

/**
 * PeerDistanceCalculator 的主要功能是计算两个节点之间的“距离”。这个距离不是物理上的地理距离，而是根据节点的 Node ID（通常是节点的公钥的哈希值）之间的 XOR 计算得出的。
 * XOR 距离的计算方法是对两个 Node ID 进行按位异或运算。结果的值越小，表示两个节点在网络拓扑结构中越接近
 *
 * 当节点加入网络或收到其他节点的请求时，PeerDistanceCalculator 可以用于计算和更新路由表中节点的距离，决定哪些节点应该保留在路由表中
 * 在响应 FINDNODE 消息时，节点会使用 PeerDistanceCalculator 来确定自己已知的与目标节点最接近的节点列表，并将这些信息作为响应发送回去
 */
public class PeerDistanceCalculator {

  /**
   * Calculates the XOR distance between two values.
   *
   * @param v1 the first value
   * @param v2 the second value
   * @return the distance
   */
  static int distance(final Bytes v1, final Bytes v2) {
    assert (v1.size() == v2.size());
    final byte[] v1b = v1.toArray();
    final byte[] v2b = v2.toArray();
    if (Arrays.equals(v1b, v2b)) {
      return 0;
    }
    int distance = v1b.length * 8;
    for (int i = 0; i < v1b.length; i++) {
      final byte xor = (byte) (0xff & (v1b[i] ^ v2b[i]));
      if (xor == 0) {
        distance -= 8;
      } else {
        int p = 7;
        while (((xor >> p--) & 0x01) == 0) {
          distance--;
        }
        break;
      }
    }
    return distance;
  }
}
