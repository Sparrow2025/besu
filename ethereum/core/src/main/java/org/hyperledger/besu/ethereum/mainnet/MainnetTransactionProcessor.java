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
package org.hyperledger.besu.ethereum.mainnet;

import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_IS_PERSISTING_PRIVATE_STATE;
import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_PRIVATE_METADATA_UPDATER;
import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_TRANSACTION;
import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_TRANSACTION_HASH;
import static org.hyperledger.besu.evm.operation.BlockHashOperation.BlockHashLookup;

import org.hyperledger.besu.collections.trie.BytesTrieSet;
import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.feemarket.CoinbaseFeePriceCalculator;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeInvalid;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.EVMWorldUpdater;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainnetTransactionProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(MainnetTransactionProcessor.class);

  private static final Set<Address> EMPTY_ADDRESS_SET = Set.of();

  protected final GasCalculator gasCalculator;

  protected final TransactionValidatorFactory transactionValidatorFactory;

  private final AbstractMessageProcessor contractCreationProcessor;

  private final AbstractMessageProcessor messageCallProcessor;

  private final int maxStackSize;

  private final boolean clearEmptyAccounts;

  protected final boolean warmCoinbase;

  protected final FeeMarket feeMarket;
  private final CoinbaseFeePriceCalculator coinbaseFeePriceCalculator;

  private final Optional<AuthorityProcessor> maybeAuthorityProcessor;

  public MainnetTransactionProcessor(
      final GasCalculator gasCalculator,
      final TransactionValidatorFactory transactionValidatorFactory,
      final AbstractMessageProcessor contractCreationProcessor,
      final AbstractMessageProcessor messageCallProcessor,
      final boolean clearEmptyAccounts,
      final boolean warmCoinbase,
      final int maxStackSize,
      final FeeMarket feeMarket,
      final CoinbaseFeePriceCalculator coinbaseFeePriceCalculator) {
    this(
        gasCalculator,
        transactionValidatorFactory,
        contractCreationProcessor,
        messageCallProcessor,
        clearEmptyAccounts,
        warmCoinbase,
        maxStackSize,
        feeMarket,
        coinbaseFeePriceCalculator,
        null);
  }

  public MainnetTransactionProcessor(
      final GasCalculator gasCalculator,
      final TransactionValidatorFactory transactionValidatorFactory,
      final AbstractMessageProcessor contractCreationProcessor,
      final AbstractMessageProcessor messageCallProcessor,
      final boolean clearEmptyAccounts,
      final boolean warmCoinbase,
      final int maxStackSize,
      final FeeMarket feeMarket,
      final CoinbaseFeePriceCalculator coinbaseFeePriceCalculator,
      final AuthorityProcessor maybeAuthorityProcessor) {
    this.gasCalculator = gasCalculator;
    this.transactionValidatorFactory = transactionValidatorFactory;
    this.contractCreationProcessor = contractCreationProcessor;
    this.messageCallProcessor = messageCallProcessor;
    this.clearEmptyAccounts = clearEmptyAccounts;
    this.warmCoinbase = warmCoinbase;
    this.maxStackSize = maxStackSize;
    this.feeMarket = feeMarket;
    this.coinbaseFeePriceCalculator = coinbaseFeePriceCalculator;
    this.maybeAuthorityProcessor = Optional.ofNullable(maybeAuthorityProcessor);
  }

  /**
   * Applies a transaction to the current system state.
   *
   * @param worldState The current world state
   * @param blockHeader The current block header
   * @param transaction The transaction to process
   * @param miningBeneficiary The address which is to receive the transaction fee
   * @param blockHashLookup The {@link BlockHashLookup} to use for BLOCKHASH operations
   * @param isPersistingPrivateState Whether the resulting private state will be persisted
   * @param transactionValidationParams Validation parameters that will be used by the {@link
   *     MainnetTransactionValidator}
   * @return the transaction result
   * @see MainnetTransactionValidator
   * @see TransactionValidationParams
   */
  public TransactionProcessingResult processTransaction(
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingPrivateState,
      final TransactionValidationParams transactionValidationParams,
      final Wei blobGasPrice) {
    return processTransaction(
        worldState,
        blockHeader,
        transaction,
        miningBeneficiary,
        OperationTracer.NO_TRACING,
        blockHashLookup,
        isPersistingPrivateState,
        transactionValidationParams,
        null,
        blobGasPrice);
  }

  /**
   * Applies a transaction to the current system state.
   *
   * @param worldState The current world state
   * @param blockHeader The current block header
   * @param transaction The transaction to process
   * @param miningBeneficiary The address which is to receive the transaction fee
   * @param blockHashLookup The {@link BlockHashLookup} to use for BLOCKHASH operations
   * @param isPersistingPrivateState Whether the resulting private state will be persisted
   * @param transactionValidationParams Validation parameters that will be used by the {@link
   *     MainnetTransactionValidator}
   * @param operationTracer operation tracer {@link OperationTracer}
   * @return the transaction result
   * @see MainnetTransactionValidator
   * @see TransactionValidationParams
   */
  public TransactionProcessingResult processTransaction(
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingPrivateState,
      final TransactionValidationParams transactionValidationParams,
      final OperationTracer operationTracer,
      final Wei blobGasPrice) {
    return processTransaction(
        worldState,
        blockHeader,
        transaction,
        miningBeneficiary,
        operationTracer,
        blockHashLookup,
        isPersistingPrivateState,
        transactionValidationParams,
        null,
        blobGasPrice);
  }

  /**
   * Applies a transaction to the current system state.
   *
   * @param worldState The current world state
   * @param blockHeader The current block header
   * @param transaction The transaction to process
   * @param miningBeneficiary The address which is to receive the transaction fee
   * @param operationTracer The tracer to record results of each EVM operation
   * @param blockHashLookup The {@link BlockHashLookup} to use for BLOCKHASH operations
   * @param isPersistingPrivateState Whether the resulting private state will be persisted
   * @return the transaction result
   */
  public TransactionProcessingResult processTransaction(
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final OperationTracer operationTracer,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingPrivateState,
      final Wei blobGasPrice) {
    return processTransaction(
        worldState,
        blockHeader,
        transaction,
        miningBeneficiary,
        operationTracer,
        blockHashLookup,
        isPersistingPrivateState,
        ImmutableTransactionValidationParams.builder().build(),
        null,
        blobGasPrice);
  }

  /**
   * Applies a transaction to the current system state.
   *
   * @param worldState The current world state
   * @param blockHeader The current block header
   * @param transaction The transaction to process
   * @param miningBeneficiary The address which is to receive the transaction fee
   * @param operationTracer The tracer to record results of each EVM operation
   * @param blockHashLookup The {@link BlockHashLookup} to use for BLOCKHASH operations
   * @param isPersistingPrivateState Whether the resulting private state will be persisted
   * @param transactionValidationParams The transaction validation parameters to use
   * @return the transaction result
   */
  public TransactionProcessingResult processTransaction(
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final OperationTracer operationTracer,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingPrivateState,
      final TransactionValidationParams transactionValidationParams,
      final Wei blobGasPrice) {
    return processTransaction(
        worldState,
        blockHeader,
        transaction,
        miningBeneficiary,
        operationTracer,
        blockHashLookup,
        isPersistingPrivateState,
        transactionValidationParams,
        null,
        blobGasPrice);
  }

  public TransactionProcessingResult processTransaction(
      final WorldUpdater worldState, // 当前世界状态的临时更新对象，它允许对账户和存储的临时更改，直到最终确定是否应用这些更改
      final ProcessableBlockHeader blockHeader, // 与正在处理的区块相关的头信息，比如区块号、时间戳、难度等
      final Transaction transaction, // 当前处理的交易对象，它包含了交易的所有关键信息，比如发送者、接收者、价值、gas 等
      final Address miningBeneficiary, // 当前区块的矿工地址，用于奖励矿工
      final OperationTracer operationTracer, // 用于追踪虚拟机执行过程中每个操作的对象，通常用于调试或分析
      final BlockHashLookup blockHashLookup, // 提供区块哈希的查找功能，便于智能合约在执行过程中查找区块哈希
      final Boolean isPersistingPrivateState, // 决定是否在处理后持久化状态，如果为 true，处理后的世界状态会被保存
      final TransactionValidationParams transactionValidationParams, // 用于控制交易验证的各种参数，如是否跳过特定的验证规则
      final PrivateMetadataUpdater privateMetadataUpdater, // 用于私有交易处理时的元数据更新
      final Wei blobGasPrice // blob gas
  ) {
    final EVMWorldUpdater evmWorldUpdater = new EVMWorldUpdater(worldState);
    try {
      final var transactionValidator = transactionValidatorFactory.get();
      LOG.trace("Starting execution of {}", transaction);
      ValidationResult<TransactionInvalidReason> validationResult =
          transactionValidator.validate(
              transaction,
              blockHeader.getBaseFee(),
              Optional.ofNullable(blobGasPrice),
              transactionValidationParams);
      // Make sure the transaction is intrinsically valid before trying to
      // compare against a sender account (because the transaction may not
      // be signed correctly to extract the sender).
      if (!validationResult.isValid()) {
        LOG.debug("Invalid transaction: {}", validationResult.getErrorMessage());
        return TransactionProcessingResult.invalid(validationResult);
      }

      final Address senderAddress = transaction.getSender();
      // 获取sender
      final MutableAccount sender = evmWorldUpdater.getOrCreateSenderAccount(senderAddress);
      validationResult = transactionValidator.validateForSender(transaction, sender, transactionValidationParams);
      if (!validationResult.isValid()) {
        LOG.debug("Invalid transaction: {}", validationResult.getErrorMessage());
        return TransactionProcessingResult.invalid(validationResult);
      }
      operationTracer.tracePrepareTransaction(evmWorldUpdater, transaction);
      final Set<Address> addressList = new BytesTrieSet<>(Address.SIZE);
      if (transaction.getAuthorizationList().isPresent()) {
        if (maybeAuthorityProcessor.isEmpty()) {
          throw new RuntimeException("Authority processor is required for 7702 transactions");
        }

        maybeAuthorityProcessor.get().addContractToAuthority(evmWorldUpdater, transaction);
        addressList.addAll(evmWorldUpdater.authorizedCodeService().getAuthorities());
      }
      // nonce + 1
      final long previousNonce = sender.incrementNonce();
      LOG.trace(
          "Incremented sender {} nonce ({} -> {})",
          senderAddress,
          previousNonce,
          sender.getNonce());
      // 计算交易费用
      final Wei transactionGasPrice = feeMarket.getTransactionPriceCalculator().price(transaction, blockHeader.getBaseFee());
      // 计算blob费用
      final long blobGas = gasCalculator.blobGasCost(transaction.getBlobCount());
      // 预计费用
      final Wei upfrontGasCost = transaction.getUpfrontGasCost(transactionGasPrice, blobGasPrice, blobGas);
      // 扣除费用
      final Wei previousBalance = sender.decrementBalance(upfrontGasCost);
      LOG.trace(
          "Deducted sender {} upfront gas cost {} ({} -> {})",
          senderAddress,
          upfrontGasCost,
          previousBalance,
          sender.getBalance());
      // 需要访问的地址列表
      final List<AccessListEntry> accessListEntries = transaction.getAccessList().orElse(List.of());
      // we need to keep a separate hash set of addresses in case they specify no storage.
      // No-storage is a common pattern, especially for Externally Owned Accounts
      final Multimap<Address, Bytes32> storageList = HashMultimap.create();
      int accessListStorageCount = 0;
      for (final var entry : accessListEntries) {
        final Address address = entry.address();
        addressList.add(address);
        final List<Bytes32> storageKeys = entry.storageKeys();
        storageList.putAll(address, storageKeys);
        accessListStorageCount += storageKeys.size();
      }
      // 在交易或区块处理中，出块奖励接收者（coinbase）的账户被认为是“热的”，无需支付高额的“冷访问” gas 成本。这有利于优化矿工在其区块奖励和相关操作中的 gas 开销
      if (warmCoinbase) {
        addressList.add(miningBeneficiary);
      }
      // 计算出交易需要消耗的固有的cost
      final long intrinsicGas = gasCalculator.transactionIntrinsicGasCost(transaction.getPayload(), transaction.isContractCreation());
      // 访问地址消耗的gas
      final long accessListGas = gasCalculator.accessListGasCost(accessListEntries.size(), accessListStorageCount);
      // 设置代码消耗的gas(这里主要对AA账户进行授权操作)
      final long setCodeGas = gasCalculator.setCodeListGasCost(transaction.authorizationListSize());
      // 减去上面3个开销后，还剩余的可用gas
      final long gasAvailable = transaction.getGasLimit() - intrinsicGas - accessListGas - setCodeGas;
      LOG.trace(
          "Gas available for execution {} = {} - {} - {} - {} (limit - intrinsic - accessList - setCode)",
          gasAvailable,
          transaction.getGasLimit(),
          intrinsicGas,
          accessListGas,
          setCodeGas);
      final WorldUpdater worldUpdater = evmWorldUpdater.updater();
      final ImmutableMap.Builder<String, Object> contextVariablesBuilder =
          ImmutableMap.<String, Object>builder()
              .put(KEY_IS_PERSISTING_PRIVATE_STATE, isPersistingPrivateState)
              .put(KEY_TRANSACTION, transaction)
              .put(KEY_TRANSACTION_HASH, transaction.getHash());
      if (privateMetadataUpdater != null) {
        contextVariablesBuilder.put(KEY_PRIVATE_METADATA_UPDATER, privateMetadataUpdater);
      }
      operationTracer.traceStartTransaction(worldUpdater, transaction);
      final MessageFrame.Builder commonMessageFrameBuilder =
          MessageFrame.builder()
              .maxStackSize(maxStackSize)
              .worldUpdater(worldUpdater.updater())
              .initialGas(gasAvailable)
              .originator(senderAddress)
              .gasPrice(transactionGasPrice)
              .blobGasPrice(blobGasPrice)
              .sender(senderAddress)
              .value(transaction.getValue())
              .apparentValue(transaction.getValue())
              .blockValues(blockHeader)
              .completer(__ -> {})
              .miningBeneficiary(miningBeneficiary)
              .blockHashLookup(blockHashLookup)
              .contextVariables(contextVariablesBuilder.build())
              .accessListWarmAddresses(addressList)
              .accessListWarmStorage(storageList);

      if (transaction.getVersionedHashes().isPresent()) {
        commonMessageFrameBuilder.versionedHashes(
            Optional.of(transaction.getVersionedHashes().get().stream().toList()));
      } else {
        commonMessageFrameBuilder.versionedHashes(Optional.empty());
      }

      final MessageFrame initialFrame;
      if (transaction.isContractCreation()) {
        // 生成CA地址
        final Address contractAddress = Address.contractAddress(senderAddress, sender.getNonce() - 1L);
        final Bytes initCodeBytes = transaction.getPayload();
        Code code = contractCreationProcessor.getCodeFromEVMForCreation(initCodeBytes);
        initialFrame =
            commonMessageFrameBuilder
                .type(MessageFrame.Type.CONTRACT_CREATION)
                .address(contractAddress)
                .contract(contractAddress)
                .inputData(initCodeBytes.slice(code.getSize()))
                .code(code)
                .build();
      } else {
        @SuppressWarnings("OptionalGetWithoutIsPresent") // isContractCall tests isPresent
        final Address to = transaction.getTo().get();
        // 获取接收者账户信息，这里是处理接收者是CA的情况. 因为可能涉及到执行合约的代码，所以需要先获取合约的代码。MessageFrame的定义就是包含所有信息
        final Optional<Account> maybeContract = Optional.ofNullable(evmWorldUpdater.get(to));
        initialFrame =
            commonMessageFrameBuilder
                .type(MessageFrame.Type.MESSAGE_CALL)
                .address(to)
                .contract(to)
                .inputData(transaction.getPayload())
                .code(maybeContract.map(c -> messageCallProcessor.getCodeFromEVM(c.getCodeHash(), c.getCode())).orElse(CodeV0.EMPTY_CODE))
                .build();
      }
      Deque<MessageFrame> messageFrameStack = initialFrame.getMessageFrameStack();
      if (initialFrame.getCode().isValid()) {
        while (!messageFrameStack.isEmpty()) {
          // 这里循环执行预编码的合约，直到栈为空
          process(messageFrameStack.peekFirst(), operationTracer);
        }
      } else {
        initialFrame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
        initialFrame.setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.INVALID_CODE));
        validationResult =
            ValidationResult.invalid(
                TransactionInvalidReason.EOF_CODE_INVALID,
                ((CodeInvalid) initialFrame.getCode()).getInvalidReason());
      }
      // 如果所有的预编码合约执行完毕，就提交世界状态
      if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
        worldUpdater.commit();
      } else {
        if (initialFrame.getExceptionalHaltReason().isPresent()
            && initialFrame.getCode().isValid()) {
          validationResult =
              ValidationResult.invalid(
                  TransactionInvalidReason.EXECUTION_HALTED,
                  initialFrame.getExceptionalHaltReason().get().toString());
        }
      }

      if (LOG.isTraceEnabled()) {
        LOG.trace(
            "Gas used by transaction: {}, by message call/contract creation: {}",
            transaction.getGasLimit() - initialFrame.getRemainingGas(),
            gasAvailable - initialFrame.getRemainingGas());
      }

      // Refund the sender by what we should and pay the miner fee (note that we're doing them one
      // after the other so that if it is the same account somehow, we end up with the right result)
      // 在以太坊中，当合约执行 SELFDESTRUCT 指令时，可以获得一部分 Gas 返还，主要是为了奖励清理区块链状态，减轻状态增长的压力
      // 自毁合约时，释放了占用的存储空间，因此会获得 Gas 返还作为奖励(多少单位)
      final long selfDestructRefund = gasCalculator.getSelfDestructRefundAmount() * initialFrame.getSelfDestructs().size();
      // 基础费用的返还(多少单位)
      final long baseRefundGas = initialFrame.getGasRefund() + selfDestructRefund;
      // 计算返还的Gas(多少单位)
      final long refundedGas = refunded(transaction, initialFrame.getRemainingGas(), baseRefundGas);
      // 计算返还的Wei
      final Wei refundedWei = transactionGasPrice.multiply(refundedGas);
      // 返还之前的余额
      final Wei balancePriorToRefund = sender.getBalance();
      sender.incrementBalance(refundedWei);
      LOG.atTrace()
          .setMessage("refunded sender {}  {} wei ({} -> {})")
          .addArgument(senderAddress)
          .addArgument(refundedWei)
          .addArgument(balancePriorToRefund)
          .addArgument(sender.getBalance())
          .log();
      // 计算交易的Gas使用量
      final long gasUsedByTransaction = transaction.getGasLimit() - initialFrame.getRemainingGas();
      // update the coinbase
      final long usedGas = transaction.getGasLimit() - refundedGas;
      final CoinbaseFeePriceCalculator coinbaseCalculator;
      if (blockHeader.getBaseFee().isPresent()) {
        final Wei baseFee = blockHeader.getBaseFee().get();
        if (transactionGasPrice.compareTo(baseFee) < 0) {
          return TransactionProcessingResult.failed(
              gasUsedByTransaction,
              refundedGas,
              ValidationResult.invalid(
                  TransactionInvalidReason.TRANSACTION_PRICE_TOO_LOW,
                  "transaction price must be greater than base fee"),
              Optional.empty());
        }
        coinbaseCalculator = coinbaseFeePriceCalculator;
      } else {
        coinbaseCalculator = CoinbaseFeePriceCalculator.frontier();
      }
      // 挖矿着应该从交易中得到的奖励
      final Wei coinbaseWeiDelta = coinbaseCalculator.price(usedGas, transactionGasPrice, blockHeader.getBaseFee());
      operationTracer.traceBeforeRewardTransaction(worldUpdater, transaction, coinbaseWeiDelta);
      // 区块奖励
      final var coinbase = evmWorldUpdater.getOrCreate(miningBeneficiary);
      coinbase.incrementBalance(coinbaseWeiDelta);
      evmWorldUpdater.authorizedCodeService().resetAuthorities();
      operationTracer.traceEndTransaction(
          worldUpdater,
          transaction,
          initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS,
          initialFrame.getOutputData(),
          initialFrame.getLogs(),
          gasUsedByTransaction,
          initialFrame.getSelfDestructs(),
          0L);
      initialFrame.getSelfDestructs().forEach(evmWorldUpdater::deleteAccount);
      if (clearEmptyAccounts) {
        evmWorldUpdater.clearAccountsThatAreEmpty();
      }
      if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
        return TransactionProcessingResult.successful(
            initialFrame.getLogs(),
            gasUsedByTransaction,
            refundedGas,
            initialFrame.getOutputData(),
            validationResult);
      } else {
        if (initialFrame.getExceptionalHaltReason().isPresent()) {
          LOG.debug(
              "Transaction {} processing halted: {}",
              transaction.getHash(),
              initialFrame.getExceptionalHaltReason().get());
        }
        if (initialFrame.getRevertReason().isPresent()) {
          LOG.debug(
              "Transaction {} reverted: {}",
              transaction.getHash(),
              initialFrame.getRevertReason().get());
        }
        return TransactionProcessingResult.failed(
            gasUsedByTransaction, refundedGas, validationResult, initialFrame.getRevertReason());
      }
    } catch (final MerkleTrieException re) {
      operationTracer.traceEndTransaction(
          evmWorldUpdater.updater(),
          transaction,
          false,
          Bytes.EMPTY,
          List.of(),
          0,
          EMPTY_ADDRESS_SET,
          0L);

      // need to throw to trigger the heal
      throw re;
    } catch (final RuntimeException re) {
      operationTracer.traceEndTransaction(
          evmWorldUpdater.updater(),
          transaction,
          false,
          Bytes.EMPTY,
          List.of(),
          0,
          EMPTY_ADDRESS_SET,
          0L);

      LOG.error("Critical Exception Processing Transaction", re);
      return TransactionProcessingResult.invalid(
          ValidationResult.invalid(
              TransactionInvalidReason.INTERNAL_ERROR,
              "Internal Error in Besu - " + re + "\n" + printableStackTraceFromThrowable(re)));
    }
  }

  public void process(final MessageFrame frame, final OperationTracer operationTracer) {
    final AbstractMessageProcessor executor = getMessageProcessor(frame.getType());
    executor.process(frame, operationTracer);
  }

  public AbstractMessageProcessor getMessageProcessor(final MessageFrame.Type type) {
    return switch (type) {
      case MESSAGE_CALL -> messageCallProcessor;
      case CONTRACT_CREATION -> contractCreationProcessor;
    };
  }

  protected long refunded(
      final Transaction transaction, final long gasRemaining, final long gasRefund) {
    // Integer truncation takes care of the floor calculation needed after the divide.
    final long maxRefundAllowance =
        (transaction.getGasLimit() - gasRemaining) / gasCalculator.getMaxRefundQuotient();
    final long refundAllowance = Math.min(maxRefundAllowance, gasRefund);
    return gasRemaining + refundAllowance;
  }

  private String printableStackTraceFromThrowable(final RuntimeException re) {
    final StringBuilder builder = new StringBuilder();

    for (final StackTraceElement stackTraceElement : re.getStackTrace()) {
      builder.append("\tat ").append(stackTraceElement.toString()).append("\n");
    }

    return builder.toString();
  }
}
