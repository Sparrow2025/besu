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
package org.hyperledger.besu.ethereum.mainnet;

import static org.hyperledger.besu.evm.account.Account.MAX_NONCE;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Blob;
import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.KZGCommitment;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import ethereum.ckzg4844.CKZG4844JNI;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.crypto.digests.SHA256Digest;

/**
 * Validates a transaction based on Frontier protocol runtime requirements.
 *
 * <p>The {@link MainnetTransactionValidator} performs the intrinsic gas cost check on the given
 * {@link Transaction}.
 */
public class MainnetTransactionValidator implements TransactionValidator {

  private final GasCalculator gasCalculator;
  private final GasLimitCalculator gasLimitCalculator;
  private final FeeMarket feeMarket;

  private final boolean disallowSignatureMalleability;

  private final Optional<BigInteger> chainId;

  private final Set<TransactionType> acceptedTransactionTypes;

  private final int maxInitcodeSize;

  public MainnetTransactionValidator(
      final GasCalculator gasCalculator,
      final GasLimitCalculator gasLimitCalculator,
      final FeeMarket feeMarket,
      final boolean checkSignatureMalleability,
      final Optional<BigInteger> chainId,
      final Set<TransactionType> acceptedTransactionTypes,
      final int maxInitcodeSize) {
    this.gasCalculator = gasCalculator;
    this.gasLimitCalculator = gasLimitCalculator;
    this.feeMarket = feeMarket;
    this.disallowSignatureMalleability = checkSignatureMalleability;
    this.chainId = chainId;
    this.acceptedTransactionTypes = acceptedTransactionTypes;
    this.maxInitcodeSize = maxInitcodeSize;
  }

  @Override
  public ValidationResult<TransactionInvalidReason> validate(
      final Transaction transaction,
      final Optional<Wei> baseFee,
      final Optional<Wei> blobFee,
      final TransactionValidationParams transactionValidationParams) {
    // 验证签名
    final ValidationResult<TransactionInvalidReason> signatureResult = validateTransactionSignature(transaction);
    if (!signatureResult.isValid()) {
      return signatureResult;
    }
    // 验证blob交易
    if (transaction.getType().supportsBlob()) {
      final ValidationResult<TransactionInvalidReason> blobTransactionResult = validateBlobTransaction(transaction);
      if (!blobTransactionResult.isValid()) {
        return blobTransactionResult;
      }

      if (transaction.getBlobsWithCommitments().isPresent()) {
        final ValidationResult<TransactionInvalidReason> blobsResult = validateTransactionsBlobs(transaction);
        if (!blobsResult.isValid()) {
          return blobsResult;
        }
      }
    }

    final TransactionType transactionType = transaction.getType();
    if (!acceptedTransactionTypes.contains(transactionType)) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_TRANSACTION_FORMAT,
          String.format(
              "Transaction type %s is invalid, accepted transaction types are %s",
              transactionType, acceptedTransactionTypes));
    }
    // 这里判断nonce是否合理
    if (transaction.getNonce() == MAX_NONCE) {
      return ValidationResult.invalid(
          TransactionInvalidReason.NONCE_OVERFLOW, "Nonce must be less than 2^64-1");
    }
    // 如果是合约创建，需要判断字节长度是否超出限制，默认是65536个字节（2^16）
    if (transaction.isContractCreation() && transaction.getPayload().size() > maxInitcodeSize) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INITCODE_TOO_LARGE,
          String.format(
              "Initcode size of %d exceeds maximum size of %s",
              transaction.getPayload().size(), maxInitcodeSize));
    }

    return validateCostAndFee(transaction, baseFee, blobFee, transactionValidationParams);
  }

  private ValidationResult<TransactionInvalidReason> validateCostAndFee(
      final Transaction transaction,
      final Optional<Wei> maybeBaseFee,
      final Optional<Wei> maybeBlobFee,
      final TransactionValidationParams transactionValidationParams) {

    if (maybeBaseFee.isPresent()) {
      // 计算出每单位gas的price
      final Wei price = feeMarket.getTransactionPriceCalculator().price(transaction, maybeBaseFee);
      // 交易的 gas price 低于网络当前的市场水平或最低要求。在以太坊中，矿工通常会优先打包那些提供较高手续费的交易，以获得更高的奖励。低于网络拥堵时的市场价的交易会被视为 "underpriced"
      if (!transactionValidationParams.allowUnderpriced()
          && price.compareTo(maybeBaseFee.orElseThrow()) < 0) {
        return ValidationResult.invalid(
            TransactionInvalidReason.GAS_PRICE_BELOW_CURRENT_BASE_FEE,
            "gasPrice is less than the current BaseFee");
      }
      // 需要大于优先费用
      // assert transaction.max_fee_per_gas >= transaction.max_priority_fee_per_gas
      if (transaction.getType().supports1559FeeMarket()
          && transaction
                  .getMaxPriorityFeePerGas()
                  .get()
                  .getAsBigInteger()
                  .compareTo(transaction.getMaxFeePerGas().get().getAsBigInteger())
              > 0) {
        return ValidationResult.invalid(
            TransactionInvalidReason.MAX_PRIORITY_FEE_PER_GAS_EXCEEDS_MAX_FEE_PER_GAS,
            "max priority fee per gas cannot be greater than max fee per gas");
      }
    }

    if (transaction.getType().supportsBlob()) {
      // 计算blob所需费用
      final long txTotalBlobGas = gasCalculator.blobGasCost(transaction.getBlobCount());
      // 检查是否超过当前区块blob最大gas的limit
      if (txTotalBlobGas > gasLimitCalculator.currentBlobGasLimit()) {
        return ValidationResult.invalid(
            TransactionInvalidReason.TOTAL_BLOB_GAS_TOO_HIGH,
            String.format(
                "total blob gas %d exceeds max blob gas per block %d",
                txTotalBlobGas, gasLimitCalculator.currentBlobGasLimit()));
      }
      if (maybeBlobFee.isEmpty()) {
        throw new IllegalArgumentException(
            "blob fee must be provided from blocks containing blobs");
        // tx.getMaxFeePerBlobGas can be empty for eth_call
      } else if (!transactionValidationParams.allowUnderpriced()
          && maybeBlobFee.get().compareTo(transaction.getMaxFeePerBlobGas().get()) > 0) {
        return ValidationResult.invalid(
            TransactionInvalidReason.BLOB_GAS_PRICE_BELOW_CURRENT_BLOB_BASE_FEE,
            String.format(
                "tx max fee per blob gas less than block blob gas fee: address %s blobGasFeeCap: %s, blobBaseFee: %s",
                transaction.getSender().toHexString(),
                transaction.getMaxFeePerBlobGas().get().toHumanReadableString(),
                maybeBlobFee.get().toHumanReadableString()));
      }
    }

    final long intrinsicGasCost = gasCalculator.transactionIntrinsicGasCost(transaction.getPayload(), transaction.isContractCreation()) // 每笔交易都有一个基本的 gas 成本，无论交易内容如何。这个成本包括了执行交易所需的基础费用
            + (transaction.getAccessList().map(gasCalculator::accessListGasCost).orElse(0L)) // 访问存储的费用
            + gasCalculator.setCodeListGasCost(transaction.authorizationListSize()); // 是与以太坊中的**授权列表（authorization list）**相关的一个概念，主要用于表示列表中包含的授权条目的数量。这些授权条目通常用于控制对某些操作或资源的访问权限
    if (Long.compareUnsigned(intrinsicGasCost, transaction.getGasLimit()) > 0) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INTRINSIC_GAS_EXCEEDS_GAS_LIMIT,
          String.format(
              "intrinsic gas cost %s exceeds gas limit %s",
              intrinsicGasCost, transaction.getGasLimit()));
    }
    // 以太坊中用于计算某个交易或合约操作的前置（upfront）gas 成本的方法。这一方法主要用于确定在执行交易之前，所需的基本 gas 成本，以确保交易能够成功执行
    if (transaction.calculateUpfrontGasCost(transaction.getMaxGasPrice(), Wei.ZERO, 0).bitLength()
        > 256) {
      return ValidationResult.invalid(
          TransactionInvalidReason.UPFRONT_COST_EXCEEDS_UINT256,
          "Upfront gas cost cannot exceed 2^256 Wei");
    }
    // 注：在以太坊的交易中，不同操作码（opcode）的执行费用确实是不同的，而这些费用通常在合约执行时通过 EVM 的指令集计算
    // 1. 操作码的执行费用是在合约实际运行时计算的。EVM 在执行合约代码时会根据具体的操作码和其执行的上下文来累积 gas 消耗
    // 2. 在合约执行之前，虽然可以计算一些基本的费用（如前置费用），但操作码的具体执行成本通常是在合约被调用并执行时动态计算的
    // 3. 在某些情况下，合约的输入数据（即 Payload）的大小会影响整体的 gas 成本。特别是在合约创建或存储操作时，数据的大小可能会直接影响初始的 gas 消耗
    // 4. 由于合约调用可以是复杂的，有多个分支和条件，事先准确预测操作码的所有费用是非常复杂的。因此，通常只计算一些基本的前置成本
    // 5. 一旦合约开始执行，EVM 会逐条计算每个操作码的成本，包括读取存储、写入存储、执行逻辑等
    return ValidationResult.valid();
  }

  @Override
  public ValidationResult<TransactionInvalidReason> validateForSender(
      final Transaction transaction,
      final Account sender,
      final TransactionValidationParams validationParams) {
    Wei senderBalance = Account.DEFAULT_BALANCE;
    long senderNonce = Account.DEFAULT_NONCE;
    Hash codeHash = Hash.EMPTY;

    if (sender != null) {
      senderBalance = sender.getBalance();
      senderNonce = sender.getNonce();
      if (sender.getCodeHash() != null) codeHash = sender.getCodeHash();
    }

    // 检查sender的balance是否能满足交易
    final Wei upfrontCost =
        transaction.getUpfrontCost(gasCalculator.blobGasCost(transaction.getBlobCount()));
    if (upfrontCost.compareTo(senderBalance) > 0) {
      return ValidationResult.invalid(
          TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE,
          String.format(
              "transaction up-front cost %s exceeds transaction sender account balance %s",
              upfrontCost.toQuantityHexString(), senderBalance.toQuantityHexString()));
    }

    if (Long.compareUnsigned(transaction.getNonce(), senderNonce) < 0) {
      return ValidationResult.invalid(
          TransactionInvalidReason.NONCE_TOO_LOW,
          String.format(
              "transaction nonce %s below sender account nonce %s",
              transaction.getNonce(), senderNonce));
    }

    if (!validationParams.isAllowFutureNonce() && senderNonce != transaction.getNonce()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.NONCE_TOO_HIGH,
          String.format(
              "transaction nonce %s does not match sender account nonce %s.",
              transaction.getNonce(), senderNonce));
    }

    if (!validationParams.isAllowContractAddressAsSender() && !codeHash.equals(Hash.EMPTY)) {
      return ValidationResult.invalid(
          TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED,
          String.format(
              "Sender %s has deployed code and so is not authorized to send transactions",
              transaction.getSender()));
    }

    return ValidationResult.valid();
  }

  private ValidationResult<TransactionInvalidReason> validateTransactionSignature(
      final Transaction transaction) {
    if (chainId.isPresent()
        && (transaction.getChainId().isPresent() && !transaction.getChainId().equals(chainId))) {
      return ValidationResult.invalid(
          TransactionInvalidReason.WRONG_CHAIN_ID,
          String.format(
              "transaction was meant for chain id %s and not this chain id %s",
              transaction.getChainId().get(), chainId.get()));
    }

    if (chainId.isEmpty() && transaction.getChainId().isPresent()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURES_NOT_SUPPORTED,
          "replay protected signatures is not supported");
    }

    final SECPSignature signature = transaction.getSignature();
    final BigInteger halfCurveOrder = SignatureAlgorithmFactory.getInstance().getHalfCurveOrder();
    if (disallowSignatureMalleability && signature.getS().compareTo(halfCurveOrder) > 0) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_SIGNATURE,
          String.format(
              "Signature s value should be less than %s, but got %s",
              halfCurveOrder, signature.getS()));
    }

    // org.bouncycastle.math.ec.ECCurve.AbstractFp.decompressPoint throws an
    // IllegalArgumentException for "Invalid point compression" for bad signatures.
    try {
      transaction.getSender();
    } catch (final IllegalArgumentException e) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_SIGNATURE,
          "sender could not be extracted from transaction signature");
    }
    return ValidationResult.valid();
  }

  public ValidationResult<TransactionInvalidReason> validateBlobTransaction(
      final Transaction transaction) {

    if (transaction.getType().supportsBlob() && transaction.getTo().isEmpty()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_TRANSACTION_FORMAT,
          "transaction blob transactions must have a to address");
    }

    if (transaction.getVersionedHashes().isEmpty()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_BLOBS,
          "transaction blob transactions must specify one or more versioned hashes");
    }

    return ValidationResult.valid();
  }

  public ValidationResult<TransactionInvalidReason> validateTransactionsBlobs(
      final Transaction transaction) {

    if (transaction.getBlobsWithCommitments().isEmpty()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_BLOBS,
          "transaction blobs are empty, cannot verify without blobs");
    }

    BlobsWithCommitments blobsWithCommitments = transaction.getBlobsWithCommitments().get();

    if (blobsWithCommitments.getBlobs().size() != blobsWithCommitments.getKzgCommitments().size()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_BLOBS,
          "transaction blobs and commitments are not the same size");
    }

    if (transaction.getVersionedHashes().isEmpty()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_BLOBS,
          "transaction versioned hashes are empty, cannot verify without versioned hashes");
    }
    final List<VersionedHash> versionedHashes = transaction.getVersionedHashes().get();

    // Layer 2 解决方案通过将大量交易打包成一个 commitment，并将其哈希值提交到 Layer 1（以太坊主链），确保 Layer 1 能够验证和跟踪这些交易的有效性，而无需处理每笔交易的细节
    for (int i = 0; i < versionedHashes.size(); i++) {
      final KZGCommitment commitment = blobsWithCommitments.getKzgCommitments().get(i);
      final VersionedHash versionedHash = versionedHashes.get(i);
      if (versionedHash.getVersionId() != VersionedHash.SHA256_VERSION_ID) {
        return ValidationResult.invalid(
            TransactionInvalidReason.INVALID_BLOBS,
            "transaction blobs commitment version is not supported. Expected "
                + VersionedHash.SHA256_VERSION_ID
                + ", found "
                + versionedHash.getVersionId());
      }
      // 数据完整性验证
      final VersionedHash calculatedVersionedHash = hashCommitment(commitment);
      if (!calculatedVersionedHash.equals(versionedHash)) {
        return ValidationResult.invalid(
            TransactionInvalidReason.INVALID_BLOBS,
            "transaction blobs commitment hash does not match commitment");
      }
    }

    final byte[] blobs =
        Bytes.wrap(blobsWithCommitments.getBlobs().stream().map(Blob::getData).toList())
            .toArrayUnsafe();

    final byte[] kzgCommitments =
        Bytes.wrap(
                blobsWithCommitments.getKzgCommitments().stream()
                    .map(kc -> (Bytes) kc.getData())
                    .toList())
            .toArrayUnsafe();

    final byte[] kzgProofs =
        Bytes.wrap(
                blobsWithCommitments.getKzgProofs().stream()
                    .map(kp -> (Bytes) kp.getData())
                    .toList())
            .toArrayUnsafe();
    // 通过 KZG 多项式承诺 技术，批量验证一组 blobs 和其相关的承诺值与证明值。这种批量验证是以太坊扩展提案 EIP-4844 实现的数据可用性保证的重要组成部分
    final boolean kzgVerification =
        CKZG4844JNI.verifyBlobKzgProofBatch(
            blobs, kzgCommitments, kzgProofs, blobsWithCommitments.getBlobs().size());

    if (!kzgVerification) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_BLOBS,
          "transaction blobs kzg proof verification failed");
    }

    return ValidationResult.valid();
  }

  private VersionedHash hashCommitment(final KZGCommitment commitment) {
    final SHA256Digest digest = new SHA256Digest();
    digest.update(commitment.getData().toArrayUnsafe(), 0, commitment.getData().size());

    final byte[] dig = new byte[digest.getDigestSize()];

    digest.doFinal(dig, 0);

    dig[0] = VersionedHash.SHA256_VERSION_ID;
    return new VersionedHash(Bytes32.wrap(dig));
  }
}
