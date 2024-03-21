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

package com.swirlds.platform.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.swirlds.platform.state.spi.WrappedWritableKVState;
import com.swirlds.platform.state.spi.WrappedWritableSingletonState;
import com.swirlds.platform.state.spi.WritableSingletonState;
import com.swirlds.platform.state.spi.WritableSingletonStateBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * This test extends the {@link WritableKVStateBaseTest}, getting all the test methods used there,
 * but this time executed on a {@link WrappedWritableKVState}.
 */
class WrappedWritableSingletonTest extends WritableSingletonStateBaseTest {
    private WritableSingletonState<String> delegate;

    @Override
    protected WritableSingletonStateBase<String> createState() {
        this.delegate = new WritableSingletonStateBase<>(COUNTRY_STATE_KEY, backingStore::get, backingStore::set);
        return new WrappedWritableSingletonState<>(delegate);
    }

    @Override
    protected String getBackingStoreValue() {
        return delegate.get();
    }

    @Test
    @DisplayName("If we commit on the wrapped state, the commit goes to the delegate, but not the" + " backing store")
    void commitGoesToDelegateNotBackingStore() {
        final var state = createState();
        state.put(BRAZIL);

        // The value should be in the wrapped state, but not in the delegate
        assertThat(state.get()).isEqualTo(BRAZIL); // Has the new value
        assertThat(delegate.get()).isEqualTo(AUSTRALIA); // Has the old value

        // After committing, the values MUST be flushed to the delegate
        state.commit();
        assertThat(state.get()).isEqualTo(BRAZIL); // Has the new value
        assertThat(delegate.get()).isEqualTo(BRAZIL); // Has the new value
    }
}
