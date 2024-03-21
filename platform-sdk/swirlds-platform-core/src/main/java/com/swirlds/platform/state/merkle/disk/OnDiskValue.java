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

package com.swirlds.platform.state.merkle.disk;

import static com.swirlds.platform.state.merkle.StateUtils.readFromStream;
import static com.swirlds.platform.state.merkle.StateUtils.writeToStream;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.state.merkle.StateMetadata;
import com.swirlds.virtualmap.VirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.Objects;

/**
 * A {@link VirtualValue} used for storing the actual value. In our system, a state might have
 * business objects as values, such as {@code Account} or {@code Token}. However, the {@link
 * com.swirlds.virtualmap.VirtualMap} requires each value in the map to be of the type {@link
 * VirtualValue}. Rather than exposing each service to the merkle APIs, we allow them to work in
 * terms of business objects, and this one implementation of {@link VirtualValue} is used for all
 * types of values.
 *
 * @param <V> The type of the value (business object) held in this merkel data structure
 */
public class OnDiskValue<V> implements VirtualValue {

    @Deprecated(forRemoval = true)
    private static final long CLASS_ID = 0x8837746626372L;

    static final int VERSION = 1;

    private final StateMetadata<?, V> md;
    private V value;
    private boolean immutable = false;

    // Default constructor is for deserialization
    public OnDiskValue() {
        this.md = null;
    }

    public OnDiskValue(@NonNull final StateMetadata<?, V> md) {
        this.md = Objects.requireNonNull(md);
        Objects.requireNonNull(md.stateDefinition().valueCodec());
    }

    public OnDiskValue(@NonNull final StateMetadata<?, V> md, @NonNull final V value) {
        this(md);
        this.value = Objects.requireNonNull(value);
    }

    /** {@inheritDoc} */
    @Override
    public VirtualValue copy() {
        final var copy = new OnDiskValue<>(md, value);
        this.immutable = true;
        return copy;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isImmutable() {
        return immutable;
    }

    /** {@inheritDoc} */
    @Override
    public VirtualValue asReadOnly() {
        if (isImmutable()) {
            return this;
        } else {
            final var copy = new OnDiskValue<>(md, value);
            copy.immutable = true;
            return copy;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        if (md == null) {
            throw new IllegalStateException("Cannot serialize on-disk value, null metadata / codec");
        }
        writeToStream(out, md.stateDefinition().valueCodec(), value);
    }

    /** {@inheritDoc} */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, int ignored) throws IOException {
        if (md == null) {
            throw new IllegalStateException("Cannot deserialize on-disk value, null metadata / codec");
        }
        value = readFromStream(in, md.stateDefinition().valueCodec());
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        // SHOULD NOT ALLOW md TO BE NULL, but ConstructableRegistry has foiled me.
        return md == null ? CLASS_ID : md.onDiskValueClassId();
    }

    /** {@inheritDoc} */
    @Override
    public int getVersion() {
        return 1;
    }

    /**
     * Gets the value.
     *
     * @return The value (business object)
     */
    @Nullable
    public V getValue() {
        return value;
    }

    /**
     * Sets the value
     *
     * @param value the business object value
     */
    public void setValue(@Nullable final V value) {
        throwIfImmutable();
        this.value = Objects.requireNonNull(value);
    }
}
