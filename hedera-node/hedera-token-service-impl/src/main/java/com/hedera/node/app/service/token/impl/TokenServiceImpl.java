/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.schemas.InitialModServiceTokenSchema;
import com.hedera.node.app.service.token.impl.schemas.SyntheticRecordsGenerator;
import com.swirlds.platform.state.spi.SchemaRegistry;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.SortedSet;
import java.util.function.Supplier;

/** An implementation of the {@link TokenService} interface. */
public class TokenServiceImpl implements TokenService {
    public static final String NFTS_KEY = "NFTS";
    public static final String TOKENS_KEY = "TOKENS";
    public static final String ALIASES_KEY = "ALIASES";
    public static final String ACCOUNTS_KEY = "ACCOUNTS";
    public static final String TOKEN_RELS_KEY = "TOKEN_RELS";
    public static final String STAKING_INFO_KEY = "STAKING_INFOS";
    public static final String STAKING_NETWORK_REWARDS_KEY = "STAKING_NETWORK_REWARDS";
    private final Supplier<SortedSet<Account>> sysAccts;
    private final Supplier<SortedSet<Account>> stakingAccts;
    private final Supplier<SortedSet<Account>> treasuryAccts;
    private final Supplier<SortedSet<Account>> miscAccts;
    private final Supplier<SortedSet<Account>> blocklistAccts;
    private InitialModServiceTokenSchema modTokenSchema;

    /**
     * Constructor for the token service. Each of the given suppliers should produce a {@link SortedSet}
     * of {@link Account} objects, where each account object represents a SYNTHETIC RECORD (see {@link
     * SyntheticRecordsGenerator} for more details). Even though these sorted sets contain account objects,
     * these account objects may or may not yet exist in state. They're needed for event recovery circumstances
     * @param sysAccts
     * @param stakingAccts
     * @param treasuryAccts
     * @param miscAccts
     * @param blocklistAccts
     */
    public TokenServiceImpl(
            @NonNull final Supplier<SortedSet<Account>> sysAccts,
            @NonNull final Supplier<SortedSet<Account>> stakingAccts,
            @NonNull final Supplier<SortedSet<Account>> treasuryAccts,
            @NonNull final Supplier<SortedSet<Account>> miscAccts,
            @NonNull final Supplier<SortedSet<Account>> blocklistAccts) {
        this.sysAccts = sysAccts;
        this.stakingAccts = stakingAccts;
        this.treasuryAccts = treasuryAccts;
        this.miscAccts = miscAccts;
        this.blocklistAccts = blocklistAccts;
    }

    /**
     * Necessary default constructor. See all params constructor for more details
     */
    public TokenServiceImpl() {
        this.sysAccts = Collections::emptySortedSet;
        this.stakingAccts = Collections::emptySortedSet;
        this.treasuryAccts = Collections::emptySortedSet;
        this.miscAccts = Collections::emptySortedSet;
        this.blocklistAccts = Collections::emptySortedSet;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry, @NonNull final SemanticVersion version) {
        requireNonNull(registry);
        modTokenSchema = new InitialModServiceTokenSchema(
                sysAccts, stakingAccts, treasuryAccts, miscAccts, blocklistAccts, version);
        registry.register(modTokenSchema);
    }

    public void setNftsFromState(@Nullable final VirtualMap<UniqueTokenKey, UniqueTokenValue> fs) {
        modTokenSchema.setNftsFromState(fs);
    }

    public void setTokenRelsFromState(@Nullable final VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> fs) {
        modTokenSchema.setTokenRelsFromState(fs);
    }

    public void setAcctsFromState(@Nullable final VirtualMap<EntityNumVirtualKey, OnDiskAccount> fs) {
        modTokenSchema.setAcctsFromState(fs);
    }

    public void setTokensFromState(@Nullable final MerkleMap<EntityNum, MerkleToken> fs) {
        modTokenSchema.setTokensFromState(fs);
    }

    public void setStakingFs(
            @Nullable final MerkleMap<EntityNum, MerkleStakingInfo> stakingFs,
            @Nullable final MerkleNetworkContext mnc) {
        modTokenSchema.setStakingFs(stakingFs, mnc);
    }
}
