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

package com.hedera.node.app.service.token.impl.schemas;

import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.ACCOUNT_COMPARATOR;
import static com.swirlds.platform.state.spi.HapiUtils.EMPTY_KEY_LIST;
import static com.swirlds.platform.state.spi.HapiUtils.FUNDING_ACCOUNT_EXPIRY;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.impl.BlocklistParser;
import com.swirlds.platform.state.spi.workflows.record.GenesisRecordsBuilder;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.BootstrapConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.LongStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SyntheticRecordsGenerator {
    private static final Logger log = LogManager.getLogger(SyntheticRecordsGenerator.class);
    private static final long FIRST_RESERVED_SYSTEM_CONTRACT = 350L;
    private static final long LAST_RESERVED_SYSTEM_CONTRACT = 399L;
    private static final long FIRST_POST_SYSTEM_FILE_ENTITY = 200L;

    private final BlocklistParser blocklistParser;
    private final SortedSet<Account> systemAcctRcds = new TreeSet<>(ACCOUNT_COMPARATOR);
    private final SortedSet<Account> stakingAcctRcds = new TreeSet<>(ACCOUNT_COMPARATOR);
    private final SortedSet<Account> treasuryAcctRcds = new TreeSet<>(ACCOUNT_COMPARATOR);
    private final SortedSet<Account> multiUseAcctRcds = new TreeSet<>(ACCOUNT_COMPARATOR);
    private final SortedSet<Account> blocklistAcctRcds = new TreeSet<>(ACCOUNT_COMPARATOR);

    /**
     * Create a new instance
     */
    public SyntheticRecordsGenerator() {
        blocklistParser = new BlocklistParser();
    }

    public SortedSet<Account> sysAcctRecords() {
        return systemAcctRcds;
    }

    public SortedSet<Account> stakingAcctRecords() {
        return stakingAcctRcds;
    }

    public SortedSet<Account> treasuryAcctRecords() {
        return treasuryAcctRcds;
    }

    public SortedSet<Account> multiUseAcctRecords() {
        return multiUseAcctRcds;
    }

    public SortedSet<Account> blocklistAcctRecords() {
        return blocklistAcctRcds;
    }

    /**
     * Creates the synthetic records needed in various startup scenarios (e.g. genesis or event recovery).
     * Actually, this method creates {@link Account} objects; since these objects will ultimately be
     * written to the record stream later, we'll refer to them as "records" throughout this method.
     * @param configuration the current configuration of the node
     * @param recordsKeeper the record builder that tracks the synthetic records
     */
    public void createRecords(
            @NonNull final Configuration configuration, @NonNull final GenesisRecordsBuilder recordsKeeper) {
        // We will use these various configs for creating accounts. It would be nice to consolidate them somehow
        final var accountsConfig = configuration.getConfigData(AccountsConfig.class);
        final var bootstrapConfig = configuration.getConfigData(BootstrapConfig.class);
        final var ledgerConfig = configuration.getConfigData(LedgerConfig.class);
        final var hederaConfig = configuration.getConfigData(HederaConfig.class);

        // This key is used for all system accounts
        final var superUserKey = superUserKey(bootstrapConfig);

        // Create a synthetic record for every system account. Now, it turns out that while accounts 1-100 (inclusive)
        // are system accounts, accounts 200-349 (inclusive) and 400-750 (inclusive) are "clones" of system accounts. So
        // we can just create all of these up front. Basically, every account from 1 to 750 (inclusive) other than those
        // set aside for files (101-199 inclusive) and contracts (350-399 inclusive) are the same, except for the
        // treasury account, which has a balance
        // ---------- Create system records -------------------------
        for (long num = 1; num <= ledgerConfig.numSystemAccounts(); num++) {
            final var id = asAccountId(num, hederaConfig);

            final var accountTinyBars = num == accountsConfig.treasury() ? ledgerConfig.totalTinyBarFloat() : 0;
            assert accountTinyBars >= 0L : "Negative account balance!";

            final var account = createAccount(id, accountTinyBars, bootstrapConfig.systemEntityExpiry(), superUserKey);
            systemAcctRcds.add(account);
        }
        recordsKeeper.systemAccounts(systemAcctRcds);
        log.info("Created {} synthetic system records", sysAcctRecords().size());

        // ---------- Create staking fund records -------------------------
        final var stakingRewardAccountId = asAccountId(accountsConfig.stakingRewardAccount(), hederaConfig);
        final var nodeRewardAccountId = asAccountId(accountsConfig.nodeRewardAccount(), hederaConfig);
        final var stakingFundAccounts = List.of(stakingRewardAccountId, nodeRewardAccountId);
        for (final var id : stakingFundAccounts) {
            final var stakingFundAccount = createAccount(id, 0, FUNDING_ACCOUNT_EXPIRY, EMPTY_KEY_LIST);
            stakingAcctRcds.add(stakingFundAccount);
        }
        recordsKeeper.stakingAccounts(stakingAcctRcds);
        log.info("Created {} synthetic staking records", stakingAcctRcds.size());

        // ---------- Create multi-use records -------------------------
        for (long num = 900; num <= 1000; num++) {
            final var id = asAccountId(num, hederaConfig);
            final var account = createAccount(id, 0, bootstrapConfig.systemEntityExpiry(), superUserKey);
            multiUseAcctRcds.add(account);
        }
        recordsKeeper.miscAccounts(multiUseAcctRcds);
        log.info("Created {} synthetic multi-use records", multiUseAcctRcds.size());

        // ---------- Create treasury clones -------------------------
        // Since version 0.28.6, all of these clone accounts should either all exist (on a restart) or all not exist
        // (starting from genesis)
        final var treasury = systemAcctRcds.stream()
                .filter(a -> a.accountId().accountNum() == accountsConfig.treasury())
                .findFirst()
                .orElseThrow();
        for (final var num : nonContractSystemNums(ledgerConfig.numReservedSystemEntities())) {
            final var nextClone = createAccount(
                    asAccountId(num, hederaConfig),
                    0,
                    treasury.expirationSecond(),
                    treasury.key(),
                    treasury.declineReward());
            treasuryAcctRcds.add(nextClone);
        }
        recordsKeeper.treasuryClones(treasuryAcctRcds);
        log.info(
                "Created {} zero-balance synthetic records cloning treasury properties in the {}-{} range",
                treasuryAcctRcds.size(),
                FIRST_POST_SYSTEM_FILE_ENTITY,
                ledgerConfig.numReservedSystemEntities());

        // ---------- Create blocklist records (if enabled) -------------------------
        if (accountsConfig.blocklistEnabled()) {
            final var blocklistResourceName = accountsConfig.blocklistResource();
            final var blocklist = blocklistParser.parse(blocklistResourceName);
            if (!blocklist.isEmpty()) {
                int counter = 0;
                for (final var blockedInfo : blocklist) {
                    final var acctBldr = blockedAccountWith(blockedInfo, bootstrapConfig)
                            // add a PLACEHOLDER account ID
                            .accountId(asAccountId(++counter, hederaConfig));
                    blocklistAcctRcds.add(acctBldr.build());
                }
            } else if (log.isDebugEnabled()) {
                log.debug("No blocklist accounts found in {}", blocklistResourceName);
            }
        }
        // Note: These account objects don't yet have CORRECT IDs! We will create or look up the real entity IDs when
        // the EntityIDService becomes available
        recordsKeeper.blocklistAccounts(blocklistAcctRcds);
        log.info("Created {} PLACEHOLDER synthetic blocklist records", blocklistAcctRcds.size());
    }

    /**
     * Creates a blocked Hedera account with the given memo and EVM address.
     * A blocked account has receiverSigRequired flag set to true, key set to the genesis key, and balance set to 0.
     *
     * @param blockedInfo record containing EVM address and memo for the blocked account
     * @return a Hedera account with the given memo and EVM address
     */
    @NonNull
    private Account.Builder blockedAccountWith(
            @NonNull final BlocklistParser.BlockedInfo blockedInfo, @NonNull final BootstrapConfig bootstrapConfig) {
        final var expiry = bootstrapConfig.systemEntityExpiry();
        final var acctBuilder = Account.newBuilder()
                .receiverSigRequired(true)
                .declineReward(true)
                .deleted(false)
                .expirationSecond(expiry)
                .smartContract(false)
                .key(superUserKey(bootstrapConfig))
                .autoRenewSeconds(expiry)
                .alias(blockedInfo.evmAddress());

        if (!blockedInfo.memo().isEmpty()) acctBuilder.memo(blockedInfo.memo());

        return acctBuilder;
    }

    /**
     * Given an account number and a config, produce a correct AccountID. The config is needed to
     * determine the correct shard and realm numbers.
     */
    static AccountID asAccountId(final long acctNum, final HederaConfig hederaConfig) {
        return AccountID.newBuilder()
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .accountNum(acctNum)
                .build();
    }

    @VisibleForTesting
    public static long[] nonContractSystemNums(final long numReservedSystemEntities) {
        return LongStream.rangeClosed(FIRST_POST_SYSTEM_FILE_ENTITY, numReservedSystemEntities)
                .filter(i -> i < FIRST_RESERVED_SYSTEM_CONTRACT || i > LAST_RESERVED_SYSTEM_CONTRACT)
                .toArray();
    }

    @NonNull
    private Key superUserKey(@NonNull final BootstrapConfig bootstrapConfig) {
        final var superUserKeyBytes = bootstrapConfig.genesisPublicKey();
        if (superUserKeyBytes.length() != 32) {
            throw new IllegalStateException("'" + superUserKeyBytes + "' is not a possible Ed25519 public key");
        }
        return Key.newBuilder().ed25519(superUserKeyBytes).build();
    }

    @NonNull
    private Account createAccount(
            @NonNull final AccountID id, final long balance, final long expiry, @NonNull final Key key) {
        return createAccount(id, balance, expiry, key, true);
    }

    @NonNull
    private Account createAccount(
            @NonNull final AccountID id,
            final long balance,
            final long expiry,
            final Key key,
            final boolean declineReward) {
        return Account.newBuilder()
                .accountId(id)
                .receiverSigRequired(false)
                .deleted(false)
                .expirationSecond(expiry)
                .memo("")
                .smartContract(false)
                .key(key)
                .declineReward(declineReward)
                .autoRenewSeconds(expiry)
                .maxAutoAssociations(0)
                .tinybarBalance(balance)
                .build();
    }
}
