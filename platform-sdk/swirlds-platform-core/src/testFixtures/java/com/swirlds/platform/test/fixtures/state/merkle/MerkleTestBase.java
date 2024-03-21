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

package com.swirlds.platform.test.fixtures.state.merkle;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.pbj.runtime.Codec;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.utility.Labeled;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.platform.state.merkle.MerkleHederaState;
import com.swirlds.platform.state.merkle.StateMetadata;
import com.swirlds.platform.state.merkle.StateUtils;
import com.swirlds.platform.state.merkle.disk.OnDiskKey;
import com.swirlds.platform.state.merkle.disk.OnDiskKeySerializer;
import com.swirlds.platform.state.merkle.disk.OnDiskValue;
import com.swirlds.platform.state.merkle.disk.OnDiskValueSerializer;
import com.swirlds.platform.state.merkle.memory.InMemoryKey;
import com.swirlds.platform.state.merkle.memory.InMemoryValue;
import com.swirlds.platform.state.merkle.queue.QueueNode;
import com.swirlds.platform.state.merkle.singleton.SingletonNode;
import com.swirlds.platform.state.spi.StateDefinition;
import com.swirlds.platform.test.fixtures.state.StateTestBase;
import com.swirlds.platform.test.fixtures.state.TestSchema;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * This base class provides helpful methods and defaults for simplifying the other merkle related
 * tests in this and sub packages. It is highly recommended to extend from this class.
 *
 * <h1>Services</h1>
 *
 * <p>This class introduces two real services, and one bad service. The real services are called
 * (quite unhelpfully) {@link #FIRST_SERVICE} and {@link #SECOND_SERVICE}. There is also an {@link
 * #UNKNOWN_SERVICE} which is useful for tests where we are trying to look up a service that should
 * not exist.
 *
 * <p>Each service has a number of associated states, based on those defined in {@link
 * StateTestBase}. The {@link #FIRST_SERVICE} has "fruit" and "animal" states, while the {@link
 * #SECOND_SERVICE} has space, steam, and country themed states. Most of these are simple String
 * types for the key and value, but the space themed state uses Long as the key type.
 *
 * <p>This class defines all the {@link Codec}, {@link StateMetadata}, and {@link MerkleMap}s
 * required to represent each of these. It does not create a {@link VirtualMap} automatically, but
 * does provide APIs to make it easy to create them (the {@link VirtualMap} has a lot of setup
 * complexity, and also requires a storage directory, so rather than creating these for every test
 * even if they don't need it, I just use it for virtual map specific tests).
 */
public class MerkleTestBase extends StateTestBase {
    public static final String FIRST_SERVICE = "First-Service";
    public static final String SECOND_SERVICE = "Second-Service";
    public static final String UNKNOWN_SERVICE = "Bogus-Service";

    /** A TEST ONLY {@link Codec} to be used with String data types */
    public static final Codec<String> STRING_CODEC = TestStringCodec.SINGLETON;
    /** A TEST ONLY {@link Codec} to be used with Long data types */
    public static final Codec<Long> LONG_CODEC = TestLongCodec.SINGLETON;

    /** Used by some tests that need to hash */
    protected static final MerkleCryptography CRYPTO = MerkleCryptoFactory.getInstance();

    // These longs are used with the "space" k/v state
    public static final long A_LONG_KEY = 0L;
    public static final long B_LONG_KEY = 1L;
    public static final long C_LONG_KEY = 2L;
    public static final long D_LONG_KEY = 3L;
    public static final long E_LONG_KEY = 4L;
    public static final long F_LONG_KEY = 5L;
    public static final long G_LONG_KEY = 6L;

    /**
     * This {@link ConstructableRegistry} is required for serialization tests. It is expensive to
     * configure it, so it is null unless {@link #setupConstructableRegistry()} has been called by
     * the test code.
     */
    protected ConstructableRegistry registry;

    @TempDir
    private Path virtualDbPath;

    // The "FRUIT" Map is part of FIRST_SERVICE
    protected String fruitLabel;
    protected StateMetadata<String, String> fruitMetadata;
    protected MerkleMap<InMemoryKey<String>, InMemoryValue<String, String>> fruitMerkleMap;

    // An alternative "FRUIT" Map that is also part of FIRST_SERVICE, but based on VirtualMap
    protected String fruitVirtualLabel;
    protected StateMetadata<String, String> fruitVirtualMetadata;
    protected VirtualMap<OnDiskKey<String>, OnDiskValue<String>> fruitVirtualMap;

    // The "ANIMAL" map is part of FIRST_SERVICE
    protected String animalLabel;
    protected StateMetadata<String, String> animalMetadata;
    protected MerkleMap<InMemoryKey<String>, InMemoryValue<String, String>> animalMerkleMap;

    // The "SPACE" map is part of SECOND_SERVICE and uses the long-based keys
    protected String spaceLabel;
    protected StateMetadata<Long, String> spaceMetadata;
    protected MerkleMap<InMemoryKey<Long>, InMemoryValue<Long, Long>> spaceMerkleMap;

    // The "STEAM" queue is part of FIRST_SERVICE
    protected String steamLabel;
    protected StateMetadata<String, String> steamMetadata;
    protected QueueNode<String> steamQueue;

    // The "COUNTRY" singleton is part of FIRST_SERVICE
    protected String countryLabel;
    protected StateMetadata<String, String> countryMetadata;
    protected SingletonNode<String> countrySingleton;

    /** Sets up the "Fruit" merkle map, label, and metadata. */
    protected void setupFruitMerkleMap() {
        fruitLabel = StateUtils.computeLabel(FIRST_SERVICE, FRUIT_STATE_KEY);
        fruitMerkleMap = createMerkleMap(fruitLabel);
        fruitMetadata = new StateMetadata<>(
                FIRST_SERVICE,
                new TestSchema(1),
                StateDefinition.inMemory(FRUIT_STATE_KEY, STRING_CODEC, STRING_CODEC));
    }

    /** Sets up the "Fruit" virtual map, label, and metadata. */
    protected void setupFruitVirtualMap() {
        fruitVirtualLabel = StateUtils.computeLabel(FIRST_SERVICE, FRUIT_STATE_KEY);
        fruitVirtualMetadata = new StateMetadata<>(
                FIRST_SERVICE,
                new TestSchema(1),
                StateDefinition.onDisk(FRUIT_STATE_KEY, STRING_CODEC, STRING_CODEC, 100));
        fruitVirtualMap = createVirtualMap(fruitVirtualLabel, fruitVirtualMetadata);
    }

    /** Sets up the "Animal" merkle map, label, and metadata. */
    protected void setupAnimalMerkleMap() {
        animalLabel = StateUtils.computeLabel(FIRST_SERVICE, ANIMAL_STATE_KEY);
        animalMerkleMap = createMerkleMap(animalLabel);
        animalMetadata = new StateMetadata<>(
                FIRST_SERVICE,
                new TestSchema(1),
                StateDefinition.inMemory(ANIMAL_STATE_KEY, STRING_CODEC, STRING_CODEC));
    }

    /** Sets up the "Space" merkle map, label, and metadata. */
    protected void setupSpaceMerkleMap() {
        spaceLabel = StateUtils.computeLabel(SECOND_SERVICE, SPACE_STATE_KEY);
        spaceMerkleMap = createMerkleMap(spaceLabel);
        spaceMetadata = new StateMetadata<>(
                SECOND_SERVICE, new TestSchema(1), StateDefinition.inMemory(SPACE_STATE_KEY, LONG_CODEC, STRING_CODEC));
    }

    protected void setupSingletonCountry() {
        countryLabel = StateUtils.computeLabel(FIRST_SERVICE, COUNTRY_STATE_KEY);
        countryMetadata = new StateMetadata<>(
                FIRST_SERVICE, new TestSchema(1), StateDefinition.singleton(COUNTRY_STATE_KEY, STRING_CODEC));
        countrySingleton = new SingletonNode<>(countryMetadata, AUSTRALIA);
    }

    protected void setupSteamQueue() {
        steamLabel = StateUtils.computeLabel(FIRST_SERVICE, STEAM_STATE_KEY);
        steamMetadata = new StateMetadata<>(
                FIRST_SERVICE, new TestSchema(1), StateDefinition.queue(STEAM_STATE_KEY, STRING_CODEC));
        steamQueue = new QueueNode<>(steamMetadata);
    }

    /** Sets up the {@link #registry}, ready to be used for serialization tests */
    protected void setupConstructableRegistry() {
        // Unfortunately, we need to configure the ConstructableRegistry for serialization tests and
        // even for basic usage of the MerkleMap (it uses it internally to make copies of internal
        // nodes).
        try {
            registry = ConstructableRegistry.getInstance();

            // It may have been configured during some other test, so we reset it
            registry.reset();
            registry.registerConstructables("com.swirlds.merklemap");
            registry.registerConstructables("com.swirlds.merkledb");
            registry.registerConstructables("com.swirlds.fcqueue");
            registry.registerConstructables("com.swirlds.virtualmap");
            registry.registerConstructables("com.swirlds.common.merkle");
            registry.registerConstructables("com.swirlds.common");
            registry.registerConstructables("com.swirlds.merkle");
            registry.registerConstructables("com.swirlds.merkle.tree");
        } catch (ConstructableRegistryException ex) {
            throw new AssertionError(ex);
        }
    }

    /** Creates a new arbitrary merkle map with the given label. */
    protected <K extends Comparable<K>, V> MerkleMap<InMemoryKey<K>, InMemoryValue<K, V>> createMerkleMap(
            String label) {
        final var map = new MerkleMap<InMemoryKey<K>, InMemoryValue<K, V>>();
        map.setLabel(label);
        return map;
    }

    /** Creates a new arbitrary virtual map with the given label, storageDir, and metadata */
    @SuppressWarnings("unchecked")
    protected VirtualMap<OnDiskKey<String>, OnDiskValue<String>> createVirtualMap(
            String label, StateMetadata<String, String> md) {
        final var merkleDbTableConfig = new MerkleDbTableConfig<>(
                (short) 1,
                DigestType.SHA_384,
                (short) 1,
                new OnDiskKeySerializer<>(md),
                (short) 1,
                new OnDiskValueSerializer<>(md));
        merkleDbTableConfig.hashesRamToDiskThreshold(0);
        merkleDbTableConfig.maxNumberOfKeys(100);
        merkleDbTableConfig.preferDiskIndices(true);
        final var builder = new MerkleDbDataSourceBuilder<>(virtualDbPath, merkleDbTableConfig);
        return new VirtualMap<>(label, builder);
    }

    /**
     * Looks within the merkle tree for a node with the given label. This is useful for tests that
     * need to verify some change actually happened in the merkle tree.
     */
    protected MerkleNode getNodeForLabel(MerkleHederaState hederaMerkle, String label) {
        // This is not idea, as it requires white-box testing -- knowing the
        // internal details of the MerkleHederaState. But lacking a getter
        // (which I don't want to add), this is what I'm left with!
        for (int i = 0, n = hederaMerkle.getNumberOfChildren(); i < n; i++) {
            final MerkleNode child = hederaMerkle.getChild(i);
            if (child instanceof Labeled labeled && label.equals(labeled.getLabel())) {
                return child;
            }
        }

        return null;
    }

    /** A convenience method for creating {@link SemanticVersion}. */
    protected SemanticVersion version(int major, int minor, int patch) {
        return new SemanticVersion(major, minor, patch, null, null);
    }

    /** A convenience method for adding a k/v pair to a merkle map */
    protected void add(
            MerkleMap<InMemoryKey<String>, InMemoryValue<String, String>> map,
            StateMetadata<String, String> md,
            String key,
            String value) {
        final var def = md.stateDefinition();
        final var k = new InMemoryKey<>(key);
        map.put(k, new InMemoryValue<>(md, k, value));
    }

    /** A convenience method for adding a k/v pair to a virtual map */
    protected void add(
            VirtualMap<OnDiskKey<String>, OnDiskValue<String>> map,
            StateMetadata<String, String> md,
            String key,
            String value) {
        final var k = new OnDiskKey<>(md, key);
        map.put(k, new OnDiskValue<>(md, value));
    }

    /** A convenience method used to serialize a merkle tree */
    protected byte[] writeTree(@NonNull final MerkleNode tree, @NonNull final Path tempDir) throws IOException {
        final var byteOutputStream = new ByteArrayOutputStream();
        try (final var out = new MerkleDataOutputStream(byteOutputStream)) {
            out.writeMerkleTree(tempDir, tree);
        }
        return byteOutputStream.toByteArray();
    }

    /** A convenience method used to deserialize a merkle tree */
    protected <T extends MerkleNode> T parseTree(@NonNull final byte[] state, @NonNull final Path tempDir)
            throws IOException {
        // Restore to a fresh MerkleDb instance
        MerkleDb.resetDefaultInstancePath();
        final var byteInputStream = new ByteArrayInputStream(state);
        try (final var in = new MerkleDataInputStream(byteInputStream)) {
            return in.readMerkleTree(tempDir, 100);
        }
    }

    @AfterEach
    void cleanUp() {
        MerkleDb.resetDefaultInstancePath();
    }
}
