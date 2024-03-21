/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ALIAS_ALREADY_ASSIGNED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_INITIAL_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.CryptoCreateHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoHandlerTestBase;
import com.hedera.node.app.service.token.impl.validators.CryptoCreateValidator;
import com.hedera.node.app.service.token.impl.validators.StakingValidator;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.swirlds.platform.state.spi.info.NetworkInfo;
import com.swirlds.platform.state.spi.info.NodeInfo;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.handle.validation.StandardizedAttributeValidator;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link CryptoCreateHandler}.
 */
@ExtendWith(MockitoExtension.class)
class CryptoCreateHandlerTest extends CryptoHandlerTestBase {
    @Mock(strictness = LENIENT)
    private HandleContext handleContext;

    @Mock(strictness = Strictness.LENIENT)
    private LongSupplier consensusSecondNow;

    @Mock(strictness = LENIENT)
    private GlobalDynamicProperties dynamicProperties;

    @Mock
    private PropertySource compositeProps;

    @Mock
    private CryptoCreateRecordBuilder recordBuilder;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private NodeInfo nodeInfo;

    @Mock(strictness = LENIENT)
    private ExpiryValidator expiryValidator;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private FeeAccumulator feeAccumulator;

    private CryptoCreateHandler subject;

    private CryptoCreateValidator cryptoCreateValidator;
    private StakingValidator stakingValidator;
    private AttributeValidator attributeValidator;
    private TransactionBody txn;

    private Configuration configuration;
    private static final long defaultInitialBalance = 100L;
    private static final Fees fee = new Fees(1, 1, 1);
    private static final long stakeNodeId = 3L;

    @BeforeEach
    public void setUp() {
        super.setUp();
        refreshStoresWithCurrentTokenInWritable();
        txn = new CryptoCreateBuilder().build();
        given(handleContext.body()).willReturn(txn);
        given(handleContext.recordBuilder(any())).willReturn(recordBuilder);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableStore);

        given(dynamicProperties.maxMemoUtf8Bytes()).willReturn(100);
        given(dynamicProperties.minAutoRenewDuration()).willReturn(2592000L);
        lenient().when(dynamicProperties.maxAutoRenewDuration()).thenReturn(8000001L);
        attributeValidator = new StandardizedAttributeValidator(consensusSecondNow, compositeProps, dynamicProperties);
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        given(handleContext.feeCalculator(SubType.DEFAULT)).willReturn(feeCalculator);
        lenient().when(handleContext.feeAccumulator()).thenReturn(feeAccumulator);
        lenient().when(feeCalculator.legacyCalculate(any())).thenReturn(new Fees(1, 1, 1));
        lenient().when(feeCalculator.addBytesPerTransaction(anyLong())).thenReturn(feeCalculator);
        lenient().when(feeCalculator.addStorageBytesSeconds(anyLong())).thenReturn(feeCalculator);
        lenient().when(feeCalculator.addRamByteSeconds(anyLong())).thenReturn(feeCalculator);
        lenient().when(feeCalculator.addNetworkRamByteSeconds(anyLong())).thenReturn(feeCalculator);

        cryptoCreateValidator = new CryptoCreateValidator();
        stakingValidator = new StakingValidator();
        given(handleContext.networkInfo()).willReturn(networkInfo);
        subject = new CryptoCreateHandler(cryptoCreateValidator, stakingValidator);
    }

    @Test
    @DisplayName("preHandle works when there is a receiverSigRequired")
    void preHandleCryptoCreateVanilla() throws PreCheckException {
        final var context = new FakePreHandleContext(readableStore, txn);
        subject.pureChecks(txn);
        subject.preHandle(context);

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 1);
        assertEquals(key, context.payerKey());
    }

    @Test
    @DisplayName("pureChecks fail when initial balance is not greater than zero")
    void whenInitialBalanceIsNegative() throws PreCheckException {
        txn = new CryptoCreateBuilder().withInitialBalance(-1L).build();
        final var msg = assertThrows(PreCheckException.class, () -> subject.pureChecks(txn));
        assertEquals(INVALID_INITIAL_BALANCE, msg.responseCode());
    }

    @Test
    @DisplayName("pureChecks fail without auto-renew period specified")
    void whenNoAutoRenewPeriodSpecified() throws PreCheckException {
        txn = new CryptoCreateBuilder().withNoAutoRenewPeriod().build();
        final var msg = assertThrows(PreCheckException.class, () -> subject.pureChecks(txn));
        assertEquals(INVALID_RENEWAL_PERIOD, msg.responseCode());
    }

    @Test
    @DisplayName("pureChecks fail when negative send record threshold is specified")
    void sendRecordThresholdIsNegative() throws PreCheckException {
        txn = new CryptoCreateBuilder().withSendRecordThreshold(-1).build();
        final var msg = assertThrows(PreCheckException.class, () -> subject.pureChecks(txn));
        assertEquals(INVALID_SEND_RECORD_THRESHOLD, msg.responseCode());
    }

    @Test
    @DisplayName("pureChecks fail when negative receive record threshold is specified")
    void receiveRecordThresholdIsNegative() throws PreCheckException {
        txn = new CryptoCreateBuilder().withReceiveRecordThreshold(-1).build();
        final var msg = assertThrows(PreCheckException.class, () -> subject.pureChecks(txn));
        assertEquals(INVALID_RECEIVE_RECORD_THRESHOLD, msg.responseCode());
    }

    @Test
    @DisplayName("pureChecks fail when proxy accounts id is specified")
    void whenProxyAccountIdIsSpecified() throws PreCheckException {
        txn = new CryptoCreateBuilder().withProxyAccountNum(1).build();
        final var msg = assertThrows(PreCheckException.class, () -> subject.pureChecks(txn));
        assertEquals(PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED, msg.responseCode());
    }

    @Test
    @DisplayName("preHandle succeeds when initial balance is zero")
    void preHandleWorksWhenInitialBalanceIsZero() throws PreCheckException {
        txn = new CryptoCreateBuilder().withInitialBalance(0L).build();
        final var context = new FakePreHandleContext(readableStore, txn);
        subject.pureChecks(txn);
        subject.preHandle(context);

        assertEquals(txn, context.body());
        basicMetaAssertions(context, 1);
        assertEquals(key, context.payerKey());
    }

    @Test
    @DisplayName("preHandle works when there is no receiverSigRequired")
    void noReceiverSigRequiredPreHandleCryptoCreate() throws PreCheckException {
        final var noReceiverSigTxn =
                new CryptoCreateBuilder().withReceiverSigReq(false).build();
        final var expected = new FakePreHandleContext(readableStore, noReceiverSigTxn);

        final var context = new FakePreHandleContext(readableStore, noReceiverSigTxn);
        subject.preHandle(context);

        assertEquals(expected.body(), context.body());
        assertFalse(context.requiredNonPayerKeys().contains(key));
        basicMetaAssertions(context, 0);
        assertThat(context.requiredNonPayerKeys()).isEmpty();
        assertEquals(key, context.payerKey());
    }

    @Test
    @DisplayName("handle works when account can be created without any alias")
    // Suppressing the warning that we have too many assertions
    @SuppressWarnings("java:S5961")
    void handleCryptoCreateVanilla() {
        txn = new CryptoCreateBuilder().withStakedAccountId(3).build();
        given(handleContext.body()).willReturn(txn);

        given(handleContext.consensusNow()).willReturn(consensusInstant);
        given(handleContext.newEntityNum()).willReturn(1000L);
        given(handleContext.payer()).willReturn(id);
        setupConfig();
        setupExpiryValidator();

        // newly created account and payer account are not modified. Validate payers balance
        assertFalse(writableStore.modifiedAccountsInState().contains(accountID(1000L)));
        assertFalse(writableStore.modifiedAccountsInState().contains(accountID(id.accountNum())));
        assertEquals(payerBalance, writableStore.get(id).tinybarBalance());

        subject.handle(handleContext);

        // newly created account and payer account are modified
        assertTrue(writableStore.modifiedAccountsInState().contains(accountID(1000L)));
        assertTrue(writableStore.modifiedAccountsInState().contains(accountID(id.accountNum())));

        // Validate created account exists and check record builder has created account recorded
        final var createdAccount =
                writableStore.get(AccountID.newBuilder().accountNum(1000L).build());
        assertThat(createdAccount).isNotNull();
        final var accountID = AccountID.newBuilder().accountNum(1000L).build();
        verify(recordBuilder).accountID(accountID);

        // validate fields on created account
        assertTrue(createdAccount.receiverSigRequired());
        assertEquals(1000L, createdAccount.accountId().accountNum());
        assertEquals(Bytes.EMPTY, createdAccount.alias());
        assertEquals(otherKey, createdAccount.key());
        assertEquals(consensusTimestamp.seconds() + defaultAutoRenewPeriod, createdAccount.expirationSecond());
        assertEquals(defaultInitialBalance, createdAccount.tinybarBalance());
        assertEquals("Create Account", createdAccount.memo());
        assertFalse(createdAccount.deleted());
        assertEquals(0L, createdAccount.stakedToMe());
        assertEquals(-1L, createdAccount.stakePeriodStart());
        // staked node id is stored in state as negative long
        assertEquals(3, createdAccount.stakedAccountId().accountNum());
        assertFalse(createdAccount.declineReward());
        assertTrue(createdAccount.receiverSigRequired());
        assertNull(createdAccount.headTokenId());
        assertNull(createdAccount.headNftId());
        assertEquals(0L, createdAccount.headNftSerialNumber());
        assertEquals(0L, createdAccount.numberOwnedNfts());
        assertEquals(0, createdAccount.maxAutoAssociations());
        assertEquals(0, createdAccount.usedAutoAssociations());
        assertEquals(0, createdAccount.numberAssociations());
        assertFalse(createdAccount.smartContract());
        assertEquals(0, createdAccount.numberPositiveBalances());
        assertEquals(0L, createdAccount.ethereumNonce());
        assertEquals(-1L, createdAccount.stakeAtStartOfLastRewardedPeriod());
        assertNull(createdAccount.autoRenewAccountId());
        assertEquals(defaultAutoRenewPeriod, createdAccount.autoRenewSeconds());
        assertEquals(0, createdAccount.contractKvPairsNumber());
        assertTrue(createdAccount.cryptoAllowances().isEmpty());
        assertTrue(createdAccount.approveForAllNftAllowances().isEmpty());
        assertTrue(createdAccount.tokenAllowances().isEmpty());
        assertEquals(0, createdAccount.numberTreasuryTitles());
        assertFalse(createdAccount.expiredAndPendingRemoval());
        assertEquals(0, createdAccount.firstContractStorageKey().length());

        // validate payer balance reduced
        assertEquals(9_900L, writableStore.get(id).tinybarBalance());
    }

    @Test
    @DisplayName("handle works when account can be created without any alias using staked account id")
    // Suppressing the warning that we have too many assertions
    @SuppressWarnings("java:S5961")
    void handleCryptoCreateVanillaWithStakedAccountId() {
        txn = new CryptoCreateBuilder().withStakedAccountId(3).build();
        given(handleContext.body()).willReturn(txn);
        given(handleContext.payer()).willReturn(accountID(id.accountNum()));
        given(handleContext.consensusNow()).willReturn(consensusInstant);
        given(handleContext.newEntityNum()).willReturn(1000L);
        setupConfig();
        setupExpiryValidator();

        // newly created account and payer account are not modified. Validate payers balance
        assertFalse(writableStore.modifiedAccountsInState().contains(accountID(1000L)));
        assertFalse(writableStore.modifiedAccountsInState().contains(accountID(id.accountNum())));
        assertEquals(payerBalance, writableStore.get(id).tinybarBalance());

        subject.handle(handleContext);

        // newly created account and payer account are modified
        assertTrue(writableStore.modifiedAccountsInState().contains(accountID(1000L)));
        assertTrue(writableStore.modifiedAccountsInState().contains(accountID(id.accountNum())));

        // Validate created account exists and check record builder has created account recorded
        final var createdAccount =
                writableStore.get(AccountID.newBuilder().accountNum(1000L).build());
        assertThat(createdAccount).isNotNull();
        final var accountID = AccountID.newBuilder().accountNum(1000L).build();
        verify(recordBuilder).accountID(accountID);

        // validate fields on created account
        assertTrue(createdAccount.receiverSigRequired());
        assertEquals(1000L, createdAccount.accountId().accountNum());
        assertEquals(Bytes.EMPTY, createdAccount.alias());
        assertEquals(otherKey, createdAccount.key());
        assertEquals(consensusTimestamp.seconds() + defaultAutoRenewPeriod, createdAccount.expirationSecond());
        assertEquals(defaultInitialBalance, createdAccount.tinybarBalance());
        assertEquals("Create Account", createdAccount.memo());
        assertFalse(createdAccount.deleted());
        assertEquals(0L, createdAccount.stakedToMe());
        assertEquals(-1L, createdAccount.stakePeriodStart());
        // staked node id is stored in state as negative long
        assertEquals(3, createdAccount.stakedAccountId().accountNum());
        assertFalse(createdAccount.declineReward());
        assertTrue(createdAccount.receiverSigRequired());
        assertNull(createdAccount.headTokenId());
        assertNull(createdAccount.headNftId());
        assertEquals(0L, createdAccount.headNftSerialNumber());
        assertEquals(0L, createdAccount.numberOwnedNfts());
        assertEquals(0, createdAccount.maxAutoAssociations());
        assertEquals(0, createdAccount.usedAutoAssociations());
        assertEquals(0, createdAccount.numberAssociations());
        assertFalse(createdAccount.smartContract());
        assertEquals(0, createdAccount.numberPositiveBalances());
        assertEquals(0L, createdAccount.ethereumNonce());
        assertEquals(-1L, createdAccount.stakeAtStartOfLastRewardedPeriod());
        assertNull(createdAccount.autoRenewAccountId());
        assertEquals(defaultAutoRenewPeriod, createdAccount.autoRenewSeconds());
        assertEquals(0, createdAccount.contractKvPairsNumber());
        assertTrue(createdAccount.cryptoAllowances().isEmpty());
        assertTrue(createdAccount.approveForAllNftAllowances().isEmpty());
        assertTrue(createdAccount.tokenAllowances().isEmpty());
        assertEquals(0, createdAccount.numberTreasuryTitles());
        assertFalse(createdAccount.expiredAndPendingRemoval());
        assertEquals(0, createdAccount.firstContractStorageKey().length());

        // validate payer balance reduced
        assertEquals(9_900L, writableStore.get(id).tinybarBalance());
    }

    @Test
    @DisplayName("handle fails when autoRenewPeriod is not set. This should not happen as there should"
            + " be a semantic check in `preHandle` and handle workflow should reject the "
            + "transaction before reaching handle")
    void handleFailsWhenAutoRenewPeriodNotSet() {
        txn = new CryptoCreateBuilder().withNoAutoRenewPeriod().build();
        // newly created account and payer account are not modified. Validate payers balance
        assertFalse(writableStore.modifiedAccountsInState().contains(accountID(1000L)));
        assertFalse(writableStore.modifiedAccountsInState().contains(accountID(id.accountNum())));
        assertEquals(payerBalance, writableStore.get(id).tinybarBalance());

        assertThrows(NullPointerException.class, () -> subject.handle(handleContext));
    }

    @Test
    @DisplayName("handle fails when payer account can't pay for the newly created account initial balance")
    void handleFailsWhenPayerHasInsufficientBalance() {
        txn = new CryptoCreateBuilder().withInitialBalance(payerBalance + 1L).build();
        given(handleContext.body()).willReturn(txn);
        given(handleContext.networkInfo().nodeInfo(stakeNodeId)).willReturn(nodeInfo);
        given(handleContext.payer()).willReturn(accountID(id.accountNum()));
        setupConfig();
        setupExpiryValidator();

        // newly created account and payer account are not modified. Validate payers balance
        assertFalse(writableStore.modifiedAccountsInState().contains(accountID(1000L)));
        assertFalse(writableStore.modifiedAccountsInState().contains(accountID(id.accountNum())));
        assertEquals(payerBalance, writableStore.get(id).tinybarBalance());

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INSUFFICIENT_PAYER_BALANCE, msg.getStatus());

        verify(recordBuilder, never()).accountID(any());

        // newly created account and payer account are not modified
        assertFalse(writableStore.modifiedAccountsInState().contains(accountID(1000L)));
        assertFalse(writableStore.modifiedAccountsInState().contains(accountID(id.accountNum())));
    }

    @Test
    @DisplayName("handle fails when payer account is deleted")
    void handleFailsWhenPayerIsDeleted() {
        given(handleContext.networkInfo().nodeInfo(stakeNodeId)).willReturn(nodeInfo);
        given(handleContext.payer()).willReturn(accountID(id.accountNum()));
        changeAccountToDeleted();
        setupConfig();
        setupExpiryValidator();
        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ACCOUNT_DELETED, msg.getStatus());

        verify(recordBuilder, never()).accountID(any());

        // newly created account and payer account are not modified
        assertFalse(writableStore.modifiedAccountsInState().contains(accountID(1000L)));
    }

    @Test
    @DisplayName("handle fails when payer account doesn't exist")
    void handleFailsWhenPayerInvalid() {
        given(handleContext.networkInfo().nodeInfo(stakeNodeId)).willReturn(nodeInfo);
        given(handleContext.payer()).willReturn(accountID(invalidId.accountNum()));
        txn = new CryptoCreateBuilder()
                .withPayer(AccountID.newBuilder().accountNum(600L).build())
                .build();
        given(handleContext.body()).willReturn(txn);
        setupConfig();
        setupExpiryValidator();

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INVALID_PAYER_ACCOUNT_ID, msg.getStatus());

        verify(recordBuilder, never()).accountID(any());

        // newly created account and payer account are not modified
        assertFalse(writableStore.modifiedAccountsInState().contains(accountID(1000L)));
    }

    @Test
    @DisplayName("handle commits when alias is mentioned in the transaction")
    void handleCommitsAnyAlias() {
        final byte[] evmAddress = CommonUtils.unhex("6aeb3773ea468a814d954e6dec795bfee7d76e26");
        txn = new CryptoCreateBuilder()
                .withAlias(Bytes.wrap(evmAddress))
                .withStakedAccountId(3)
                .build();
        given(handleContext.body()).willReturn(txn);
        given(handleContext.payer()).willReturn(accountID(id.accountNum()));

        given(handleContext.consensusNow()).willReturn(consensusInstant);
        given(handleContext.newEntityNum()).willReturn(1000L);

        setupConfig();
        setupExpiryValidator();

        // newly created account and payer account are not modified. Validate payers balance
        assertFalse(writableStore.modifiedAccountsInState().contains(accountID(1000L)));
        assertFalse(writableStore.modifiedAccountsInState().contains(accountID(id.accountNum())));
        assertEquals(payerBalance, writableStore.get(id).tinybarBalance());

        subject.handle(handleContext);

        // newly created account and payer account are modified
        assertTrue(writableStore.modifiedAccountsInState().contains(accountID(1000L)));
        assertTrue(writableStore.modifiedAccountsInState().contains(accountID(id.accountNum())));
        assertEquals(
                Bytes.wrap(evmAddress),
                writableStore
                        .get(AccountID.newBuilder().accountNum(1000L).build())
                        .alias());
    }

    @Test
    void validateMemo() {
        txn = new CryptoCreateBuilder()
                .withStakedAccountId(3)
                .withMemo("some long memo that is too long")
                .build();
        given(handleContext.body()).willReturn(txn);
        given(dynamicProperties.maxMemoUtf8Bytes()).willReturn(2);
        setupConfig();
        setupExpiryValidator();
        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(MEMO_TOO_LONG, msg.getStatus());
    }

    @Test
    void validateKeyRequired() {
        txn = new CryptoCreateBuilder().withStakedAccountId(3).withKey(null).build();
        setupConfig();
        setupExpiryValidator();

        final var msg = assertThrows(PreCheckException.class, () -> subject.pureChecks(txn));
        assertEquals(INVALID_ALIAS_KEY, msg.responseCode());
    }

    @Test
    void validateAlias() {
        txn = new CryptoCreateBuilder()
                .withStakedAccountId(3)
                .withKey(key)
                .withAlias(Bytes.wrap("alias"))
                .build();
        given(handleContext.body()).willReturn(txn);
        given(handleContext.payer()).willReturn(accountID(id.accountNum()));
        given(handleContext.consensusNow()).willReturn(consensusInstant);
        setupConfig();
        setupExpiryValidator();

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INVALID_ALIAS_KEY, msg.getStatus());
    }

    @Test
    void validateAliasNotSupport() {
        txn = new CryptoCreateBuilder()
                .withStakedAccountId(3)
                .withKey(null)
                .withAlias(Bytes.wrap("alias"))
                .build();
        given(handleContext.body()).willReturn(txn);
        final var config = HederaTestConfigBuilder.create()
                .withValue("cryptoCreateWithAlias.enabled", false)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        setupExpiryValidator();

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(NOT_SUPPORTED, msg.getStatus());
    }

    @Test
    void validateAliasInvalid() {
        txn = new CryptoCreateBuilder()
                .withStakedAccountId(3)
                .withKey(key)
                .withAlias(Bytes.wrap("alias"))
                .build();
        given(handleContext.body()).willReturn(txn);
        given(handleContext.payer()).willReturn(accountID(id.accountNum()));
        given(handleContext.consensusNow()).willReturn(consensusInstant);
        final var config = HederaTestConfigBuilder.create()
                .withValue("cryptoCreateWithAlias.enabled", true)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        setupExpiryValidator();

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INVALID_ALIAS_KEY, msg.getStatus());
    }

    @Test
    void validateContractKey() {
        final var newContractId = ContractID.newBuilder().contractNum(1000L).build();
        txn = new CryptoCreateBuilder()
                .withStakedAccountId(3)
                .withKey(Key.newBuilder().contractID(newContractId).build())
                .build();
        given(handleContext.body()).willReturn(txn);
        given(handleContext.consensusNow()).willReturn(consensusInstant);
        given(handleContext.newEntityNum()).willReturn(1000L);
        given(handleContext.payer()).willReturn(id);
        setupConfig();
        setupExpiryValidator();

        assertDoesNotThrow(() -> subject.handle(handleContext));
    }

    @Test
    void validateKeyAlias() {
        txn = new CryptoCreateBuilder()
                .withStakedAccountId(3)
                .withKey(key)
                .withAlias(Bytes.wrap("alias"))
                .build();
        given(handleContext.body()).willReturn(txn);
        given(handleContext.payer()).willReturn(accountID(id.accountNum()));
        given(handleContext.consensusNow()).willReturn(consensusInstant);
        setupConfig();
        setupExpiryValidator();

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INVALID_ALIAS_KEY, msg.getStatus());
    }

    @Test
    void validateAliasSigned() {
        txn = new CryptoCreateBuilder()
                .withStakedAccountId(3)
                .withAlias(Bytes.wrap(evmAddress))
                .build();
        given(handleContext.body()).willReturn(txn);
        setupConfig();
        setupExpiryValidator();
        final var writableAliases = emptyWritableAliasStateBuilder()
                .value(new ProtoBytes(Bytes.wrap(evmAddress)), asAccount(accountNum))
                .build();
        given(writableStates.<ProtoBytes, AccountID>get(ALIASES)).willReturn(writableAliases);
        writableStore = new WritableAccountStore(writableStates);
        when(handleContext.writableStore(WritableAccountStore.class)).thenReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ALIAS_ALREADY_ASSIGNED, msg.getStatus());
    }

    @Test
    void validateAutoRenewPeriod() {
        txn = new CryptoCreateBuilder().withStakedAccountId(3).build();
        given(handleContext.body()).willReturn(txn);
        given(dynamicProperties.maxAutoRenewDuration()).willReturn(1000L);
        setupConfig();
        setupExpiryValidator();
        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(AUTORENEW_DURATION_NOT_IN_RANGE, msg.getStatus());
    }

    @Test
    void validateProxyAccount() {
        txn = new CryptoCreateBuilder()
                .withStakedAccountId(3)
                .withProxyAccountNum(accountNum)
                .build();
        given(handleContext.body()).willReturn(txn);
        setupConfig();
        setupExpiryValidator();

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED, msg.getStatus());
    }

    private void changeAccountToDeleted() {
        final var copy = account.copyBuilder().deleted(true).build();
        writableAccounts.put(id, copy);
        given(writableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(writableAccounts);
        writableStore = new WritableAccountStore(writableStates);
    }

    private void setupConfig() {
        final var config = HederaTestConfigBuilder.create()
                .withValue("cryptoCreateWithAlias.enabled", true)
                .withValue("ledger.maxAutoAssociations", 5000)
                .withValue("entities.limitTokenAssociations", false)
                .withValue("tokens.maxPerAccount", 1000)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
    }

    private void setupExpiryValidator() {
        given(expiryValidator.expirationStatus(notNull(), anyBoolean(), anyLong()))
                .willReturn(ResponseCodeEnum.OK);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
    }

    /**
     * A builder for {@link TransactionBody} instances.
     */
    private class CryptoCreateBuilder {
        private AccountID payer = id;
        private long initialBalance = defaultInitialBalance;
        private long autoRenewPeriod = defaultAutoRenewPeriod;
        private boolean receiverSigReq = true;
        private Bytes alias = null;
        private long sendRecordThreshold = 0;
        private long receiveRecordThreshold = 0;
        private AccountID proxyAccountId = null;
        private long stakedAccountId = 0;

        private Key key = otherKey;

        private String memo = null;

        private CryptoCreateBuilder() {}

        public TransactionBody build() {
            final var transactionID =
                    TransactionID.newBuilder().accountID(payer).transactionValidStart(consensusTimestamp);
            final var createTxnBody = CryptoCreateTransactionBody.newBuilder()
                    .key(key)
                    .receiverSigRequired(receiverSigReq)
                    .initialBalance(initialBalance)
                    .memo("Create Account")
                    .sendRecordThreshold(sendRecordThreshold)
                    .receiveRecordThreshold(receiveRecordThreshold);

            if (autoRenewPeriod > 0) {
                createTxnBody.autoRenewPeriod(
                        Duration.newBuilder().seconds(autoRenewPeriod).build());
            }
            if (alias != null) {
                createTxnBody.alias(alias);
            }
            if (proxyAccountId != null) {
                createTxnBody.proxyAccountID(proxyAccountId);
            }
            if (stakedAccountId > 0) {
                createTxnBody.stakedAccountId(
                        AccountID.newBuilder().accountNum(stakedAccountId).build());
            } else {
                createTxnBody.stakedNodeId(stakeNodeId);
            }
            if (memo != null) {
                createTxnBody.memo(memo);
            }

            return TransactionBody.newBuilder()
                    .transactionID(transactionID)
                    .cryptoCreateAccount(createTxnBody.build())
                    .build();
        }

        public CryptoCreateBuilder withPayer(final AccountID payer) {
            this.payer = payer;
            return this;
        }

        public CryptoCreateBuilder withInitialBalance(final long initialBalance) {
            this.initialBalance = initialBalance;
            return this;
        }

        public CryptoCreateBuilder withAutoRenewPeriod(final long autoRenewPeriod) {
            this.autoRenewPeriod = autoRenewPeriod;
            return this;
        }

        public CryptoCreateBuilder withProxyAccountNum(final long proxyAccountNum) {
            this.proxyAccountId =
                    AccountID.newBuilder().accountNum(proxyAccountNum).build();
            return this;
        }

        public CryptoCreateBuilder withSendRecordThreshold(final long threshold) {
            this.sendRecordThreshold = threshold;
            return this;
        }

        public CryptoCreateBuilder withReceiveRecordThreshold(final long threshold) {
            this.receiveRecordThreshold = threshold;
            return this;
        }

        public CryptoCreateBuilder withAlias(final Bytes alias) {
            this.alias = alias;
            return this;
        }

        public CryptoCreateBuilder withNoAutoRenewPeriod() {
            this.autoRenewPeriod = -1;
            return this;
        }

        public CryptoCreateBuilder withStakedAccountId(final long id) {
            this.stakedAccountId = id;
            return this;
        }

        public CryptoCreateBuilder withReceiverSigReq(final boolean receiverSigReq) {
            this.receiverSigReq = receiverSigReq;
            return this;
        }

        public CryptoCreateBuilder withMemo(final String memo) {
            this.memo = memo;
            return this;
        }

        public CryptoCreateBuilder withKey(final Key key) {
            this.key = key;
            return this;
        }
    }
}
