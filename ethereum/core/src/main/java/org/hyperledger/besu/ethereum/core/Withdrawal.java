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
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.ethereum.core.encoding.WithdrawalDecoder;
import org.hyperledger.besu.ethereum.core.encoding.WithdrawalEncoder;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;

public class Withdrawal implements org.hyperledger.besu.plugin.data.Withdrawal {
  private final UInt64 index; // 提款的唯一标识符，每个提款都会分配一个唯一的index，用于区分不同的提款操作
  private final UInt64 validatorIndex; // 该字段表示发起提款的验证者的索引。每个验证者在系统中都有一个唯一的索引，这个索引用于标识谁发起了此次提款请求。它通常与验证者的身份和质押账户绑定
  private final Address address; // 提款的目标地址
  private final GWei amount; // 提款金额

  public Withdrawal(
      final UInt64 index, final UInt64 validatorIndex, final Address address, final GWei amount) {
    this.index = index;
    this.validatorIndex = validatorIndex;
    this.address = address;
    this.amount = amount;
  }

  public static Withdrawal readFrom(final Bytes rlpBytes) {
    return readFrom(RLP.input(rlpBytes));
  }

  public static Withdrawal readFrom(final RLPInput rlpInput) {
    return WithdrawalDecoder.decode(rlpInput);
  }

  public void writeTo(final RLPOutput out) {
    WithdrawalEncoder.encode(this, out);
  }

  @Override
  public UInt64 getIndex() {
    return index;
  }

  @Override
  public UInt64 getValidatorIndex() {
    return validatorIndex;
  }

  @Override
  public Address getAddress() {
    return address;
  }

  @Override
  public GWei getAmount() {
    return amount;
  }

  @Override
  public String toString() {
    return "Withdrawal{"
        + "index="
        + index
        + ", validatorIndex="
        + validatorIndex
        + ", address="
        + address
        + ", amount="
        + amount
        + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Withdrawal that = (Withdrawal) o;
    return Objects.equals(index, that.index)
        && Objects.equals(validatorIndex, that.validatorIndex)
        && Objects.equals(address, that.address)
        && Objects.equals(amount, that.amount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, validatorIndex, address, amount);
  }
}
