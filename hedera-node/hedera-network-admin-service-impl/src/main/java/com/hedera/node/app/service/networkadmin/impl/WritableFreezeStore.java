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

package com.hedera.node.app.service.networkadmin.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.spi.WritableSingletonState;
import com.swirlds.platform.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Provides write methods for modifying underlying data storage mechanisms for
 * working with freeze states.
 */
public class WritableFreezeStore extends ReadableFreezeStoreImpl {
    /** The underlying data storage classes that hold the freeze state data. */
    private final WritableSingletonState<Timestamp> freezeTimeState;

    private final WritableSingletonState<Timestamp> lastFrozenTimeState;

    /** The underlying data storage class that holds the update file hash. */
    private final WritableSingletonState<ProtoBytes> updateFileHash;

    /**
     * Create a new {@link WritableFreezeStore} instance.
     *
     * @param states The state to use.
     */
    public WritableFreezeStore(@NonNull final WritableStates states) {
        super(states);
        requireNonNull(states);
        freezeTimeState = states.getSingleton(FreezeServiceImpl.FREEZE_TIME_KEY);
        lastFrozenTimeState = states.getSingleton(FreezeServiceImpl.LAST_FROZEN_TIME_KEY);
        updateFileHash = states.getSingleton(FreezeServiceImpl.UPGRADE_FILE_HASH_KEY);
    }

    /**
     * Sets the freeze time.
     *
     * @param freezeTime the freeze time to set; if null, clears the freeze time
     */
    public void freezeTime(@Nullable final Timestamp freezeTime) {
        freezeTimeState.put(freezeTime);
        if (freezeTime != null) {
            lastFrozenTimeState.put(freezeTime);
        }
    }

    @Override
    @Nullable
    /**
     * Gets the scheduled freeze time. If no freeze has been scheduled, returns null.
     */
    public Timestamp freezeTime() {
        return freezeTimeState.get();
    }

    @Override
    @Nullable
    /**
     * Gets the last frozen time. If no freeze has occurred, returns null.
     */
    public Timestamp lastFrozenTime() {
        return lastFrozenTimeState.get();
    }

    /**
     * Sets or clears the update file hash.
     *
     * @param updateFileHash The update file hash to set. If null, clears the update file hash.
     */
    public void updateFileHash(@Nullable final Bytes updateFileHash) {
        this.updateFileHash.put(new ProtoBytes(updateFileHash));
    }

    @Override
    @Nullable
    public Bytes updateFileHash() {
        ProtoBytes fileHash = updateFileHash.get();
        return (fileHash == null ? null : fileHash.value());
    }
}
