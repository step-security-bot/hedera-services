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

package com.hedera.node.app.state.merkle.adapters;

import com.hedera.node.app.HederaInjectionComponent;
import com.hedera.node.app.service.mono.context.StateChildrenProvider;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.swirlds.base.utility.Pair;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.state.merkle.MerkleHederaState;
import com.swirlds.platform.state.merkle.StateMetadata;
import com.swirlds.platform.state.merkle.disk.OnDiskKey;
import com.swirlds.platform.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;

/**
 * Adapts a {@link VirtualMap} constructed by {@code MerkleHederaState#MerkleStates} by "unwrapping" its
 * {@link OnDiskKey} and {@link OnDiskValue} containers, so that a {@code VirtualMap<OnDiskKey<K>, OnDiskValue<V>>}
 * appears as a {@code VirtualMapLike<K, V>}.
 *
 * <p>This allows us to use a {@link MerkleHederaState} as a {@link StateChildrenProvider} binding
 * within a {@link HederaInjectionComponent} instance, which is important while we are relying heavily on adapters
 * around {@code mono-service} components.
 */
public final class VirtualMapLikeAdapter {
    private VirtualMapLikeAdapter() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static <K extends VirtualKey, V extends VirtualValue> VirtualMapLike<K, V> unwrapping(
            final StateMetadata<K, V> md, final VirtualMap<OnDiskKey<K>, OnDiskValue<V>> real) {
        return new VirtualMapLike<>() {
            @Override
            public boolean release() {
                return real.release();
            }

            @Override
            public Hash getHash() {
                return real.getHash();
            }

            @Override
            public VirtualDataSource<OnDiskKey<K>, OnDiskValue<V>> getDataSource() {
                return real.getDataSource();
            }

            @Override
            public void extractVirtualMapData(
                    final ThreadManager threadManager,
                    final InterruptableConsumer<Pair<K, V>> handler,
                    final int threadCount)
                    throws InterruptedException {

                final var unwrappingHandler = new InterruptableConsumer<Pair<OnDiskKey<K>, OnDiskValue<V>>>() {
                    @Override
                    public void accept(final Pair<OnDiskKey<K>, OnDiskValue<V>> pair) throws InterruptedException {
                        handler.accept(
                                Pair.of(pair.left().getKey(), pair.right().getValue()));
                    }
                };
                VirtualMapMigration.extractVirtualMapData(threadManager, real, unwrappingHandler, threadCount);
            }

            @Override
            public void extractVirtualMapDataC(
                    final ThreadManager threadManager,
                    final InterruptableConsumer<Pair<K, V>> handler,
                    final int threadCount)
                    throws InterruptedException {
                VirtualMapMigration.extractVirtualMapDataC(
                        threadManager,
                        real,
                        pair -> handler.accept(
                                Pair.of(pair.left().getKey(), pair.right().getValue())),
                        threadCount);
            }

            @Override
            public void registerMetrics(final Metrics metrics) {
                real.registerMetrics(metrics);
            }

            @Override
            public long size() {
                return real.size();
            }

            @Override
            public void put(final K key, final V value) {
                final var onDiskKey = new OnDiskKey<>(md, key);
                final var onDiskValue = new OnDiskValue<>(md, value);
                real.put(onDiskKey, onDiskValue);
            }

            @Override
            public V get(final K key) {
                final var found = real.get(new OnDiskKey<>(md, key));
                return found != null ? found.getValue() : null;
            }

            @Override
            public V getForModify(final K key) {
                final var mutable = real.getForModify(new OnDiskKey<>(md, key));
                return mutable != null ? mutable.getValue() : null;
            }

            @Override
            public boolean containsKey(final K key) {
                return real.containsKey(new OnDiskKey<>(md, key));
            }

            @Override
            public boolean isEmpty() {
                return real.isEmpty();
            }

            @Override
            public V remove(final K key) {
                final var removed = real.remove(new OnDiskKey<>(md, key));
                return removed != null ? removed.getValue() : null;
            }

            @Override
            public void warm(final K key) {
                real.warm(new OnDiskKey<>(md, key));
            }
        };
    }
}
