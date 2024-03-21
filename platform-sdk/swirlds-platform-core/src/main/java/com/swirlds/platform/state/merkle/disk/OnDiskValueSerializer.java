/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.merkledb.serialize.ValueSerializer;
import com.swirlds.platform.state.merkle.StateMetadata;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * An implementation of {@link ValueSerializer}, required by the {@link
 * com.swirlds.virtualmap.VirtualMap} for creating new {@link OnDiskValue}s.
 *
 * @param <V> The type of the value in the virtual map
 */
public final class OnDiskValueSerializer<V> implements ValueSerializer<OnDiskValue<V>> {

    private static final long CLASS_ID = 0x3992113882234886L;

    private static final int VERSION = 1;

    // guesstimate of the typical size of a serialized value
    private static final int TYPICAL_SIZE = 1024;

    private final StateMetadata<?, V> md;

    // Default constructor provided for ConstructableRegistry, TO BE REMOVED ASAP
    @Deprecated(forRemoval = true)
    public OnDiskValueSerializer() {
        md = null;
    }

    /**
     * Create a new instance. This is created at registration time, it doesn't need to serialize
     * anything to disk.
     */
    public OnDiskValueSerializer(@NonNull final StateMetadata<?, V> md) {
        this.md = Objects.requireNonNull(md);
    }

    // Serializer info

    @Override
    public long getClassId() {
        // SHOULD NOT ALLOW md TO BE NULL, but ConstructableRegistry has foiled me.
        return md == null ? CLASS_ID : md.onDiskValueSerializerClassId();
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    // Value info

    @Override
    public long getCurrentDataVersion() {
        return OnDiskValue.VERSION;
    }

    // Value serialization

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int getSerializedSize(OnDiskValue<V> value) {
        assert md != null;
        final Codec<V> codec = md.stateDefinition().valueCodec();
        return codec.measureRecord(value.getValue());
    }

    @Override
    public int getTypicalSerializedSize() {
        return TYPICAL_SIZE;
    }

    @Override
    public void serialize(@NonNull final OnDiskValue<V> value, @NonNull final WritableSequentialData out) {
        assert md != null;
        final Codec<V> codec = md.stateDefinition().valueCodec();
        // Future work: https://github.com/hashgraph/pbj/issues/73
        try {
            codec.write(value.getValue(), out);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void serialize(final OnDiskValue<V> value, final ByteBuffer buffer) throws IOException {
        throw new UnsupportedOperationException();
    }

    // Value deserialization

    @Override
    public OnDiskValue<V> deserialize(@NonNull final ReadableSequentialData in) {
        assert md != null;
        final Codec<V> codec = md.stateDefinition().valueCodec();
        // Future work: https://github.com/hashgraph/pbj/issues/73
        try {
            final V value = codec.parse(in);
            return new OnDiskValue<>(md, value);
        } catch (final ParseException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public OnDiskValue<V> deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
        throw new UnsupportedOperationException();
    }
}
