/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.swirlds.platform.state.spi.WritableKVState;
import com.swirlds.platform.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Provides write methods for modifying underlying data storage mechanisms for
 * working with Tokens.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 * This class is not complete, it will be extended with other methods like remove, update etc.,
 */
public class WritableTokenStore extends ReadableTokenStoreImpl {
    /** The underlying data storage class that holds the token data. */
    private final WritableKVState<TokenID, Token> tokenState;

    /**
     * Create a new {@link WritableTokenStore} instance.
     *
     * @param states The state to use.
     */
    public WritableTokenStore(@NonNull final WritableStates states) {
        super(states);
        this.tokenState = states.get(TokenServiceImpl.TOKENS_KEY);
    }

    /**
     * Persists a new {@link Token} into the state, as well as exporting its ID to the transaction
     * receipt.
     *
     * @param token - the token to be mapped onto a new {@link MerkleToken} and persisted.
     */
    public void put(@NonNull final Token token) {
        Objects.requireNonNull(token);
        tokenState.put(token.tokenId(), Objects.requireNonNull(token));
    }

    /**
     * Returns the {@link Token} with the given number using {@link WritableKVState#getForModify}.
     * If no such token exists, returns {@code Optional.empty()}
     * @param tokenId - the id of the token to be retrieved.
     */
    @NonNull
    public Optional<Token> getForModify(final TokenID tokenId) {
        requireNonNull(tokenId);
        final var token = tokenState.getForModify(tokenId);
        return Optional.ofNullable(token);
    }

    /**
     * Returns the number of tokens in the state.
     * @return the number of tokens in the state.
     */
    public long sizeOfState() {
        return tokenState.size();
    }

    /**
     * Returns the set of tokens modified in existing state.
     * @return the set of tokens modified in existing state
     */
    @NonNull
    public Set<TokenID> modifiedTokens() {
        return tokenState.modifiedKeys();
    }

    /**
     * Gets the original value associated with the given tokenId before any modifications were made to
     * it. The returned value will be {@code null} if the tokenId does not exist.
     *
     * @param tokenId The tokenId.
     * @return The original value, or null if there is no such tokenId in the state
     */
    @Nullable
    public Token getOriginalValue(@NonNull final TokenID tokenId) {
        requireNonNull(tokenId);
        return tokenState.getOriginalValue(tokenId);
    }
}
