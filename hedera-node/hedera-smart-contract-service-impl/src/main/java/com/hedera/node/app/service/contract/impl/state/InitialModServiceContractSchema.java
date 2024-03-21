/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.state;

import static com.hedera.node.app.service.mono.state.migration.ContractStateMigrator.bytesFromInts;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.swirlds.platform.state.spi.MigrationContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.platform.state.spi.Schema;
import com.swirlds.platform.state.spi.StateDefinition;
import com.swirlds.platform.state.spi.WritableKVStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Defines the schema for the contract service's state.
 * (FUTURE) When mod-service release is finalized, rename this class to e.g.
 * {@code Release47ContractSchema} as it will no longer be appropriate to assume
 * this schema is always correct for the current version of the software.
 */
public class InitialModServiceContractSchema extends Schema {
    private static final Logger log = LogManager.getLogger(InitialModServiceContractSchema.class);
    public static final String STORAGE_KEY = "STORAGE";
    public static final String BYTECODE_KEY = "BYTECODE";
    private static final int MAX_BYTECODES = 50_000_000;
    private static final int MAX_STORAGE_ENTRIES = 500_000_000;

    private VirtualMapLike<ContractKey, IterableContractValue> storageFromState;
    private Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> contractBytecodeFromState;

    public InitialModServiceContractSchema(@NonNull final SemanticVersion version) {
        super(version);
    }

    public void setStorageFromState(
            @Nullable final VirtualMapLike<ContractKey, IterableContractValue> storageFromState) {
        this.storageFromState = storageFromState;
    }

    public void setBytecodeFromState(
            @Nullable final Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> bytecodeFromState) {
        this.contractBytecodeFromState = bytecodeFromState;
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        if (storageFromState != null) {
            log.info("BBM: migrating contract service");

            log.info("BBM: migrating contract k/v storage...");
            final var toState = new AtomicReference<>(
                    ctx.newStates().<SlotKey, SlotValue>get(InitialModServiceContractSchema.STORAGE_KEY));
            final var numSlotInsertions = new AtomicLong();
            try {
                storageFromState.extractVirtualMapData(
                        AdHocThreadManager.getStaticThreadManager(),
                        entry -> {
                            final var contractKey = entry.left();
                            final var key = SlotKey.newBuilder()
                                    .contractID(ContractID.newBuilder().contractNum(contractKey.getContractId()))
                                    .key(bytesFromInts(contractKey.getKey()))
                                    .build();
                            final var contractVal = entry.right();
                            final var value = SlotValue.newBuilder()
                                    .value(Bytes.wrap(contractVal.getValue()))
                                    .previousKey(bytesFromInts(contractVal.getExplicitPrevKey()))
                                    .nextKey(bytesFromInts((contractVal.getExplicitNextKey())))
                                    .build();
                            toState.get().put(key, value);
                            if (numSlotInsertions.incrementAndGet() % 10_000 == 0) {
                                // Make sure we are flushing data to disk as we go
                                ((WritableKVStateBase) toState.get()).commit();
                                ctx.copyAndReleaseOnDiskState(STORAGE_KEY);
                                // And ensure we have the latest writable state
                                toState.set(ctx.newStates().get(STORAGE_KEY));
                            }
                        },
                        1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            if (toState.get().isModified()) ((WritableKVStateBase) toState.get()).commit();

            log.info("BBM: finished migrating contract storage");

            log.info("BBM: migrating contract bytecode...");
            final var bytecodeTs = new AtomicReference<>(ctx.newStates().<ContractID, Bytecode>get(BYTECODE_KEY));
            final var numBytecodeInsertions = new AtomicLong();
            final var migratedContractNums = new ArrayList<Integer>();
            try {
                contractBytecodeFromState
                        .get()
                        .extractVirtualMapData(
                                AdHocThreadManager.getStaticThreadManager(),
                                entry -> {
                                    if (VirtualBlobKey.Type.CONTRACT_BYTECODE
                                            == entry.left().getType()) {
                                        final var contractId = entry.left().getEntityNumCode();
                                        final var contents = entry.right().getData();
                                        Bytes wrappedContents;
                                        if (contents == null || contents.length < 1) {
                                            log.debug("BBM: contract contents null for contractId " + contractId);
                                            wrappedContents = Bytes.EMPTY;
                                        } else {
                                            log.debug("BBM: migrating contract contents (length "
                                                    + contents.length
                                                    + ") for contractId "
                                                    + contractId);
                                            wrappedContents = Bytes.wrap(contents);
                                        }
                                        bytecodeTs
                                                .get()
                                                .put(
                                                        ContractID.newBuilder()
                                                                .contractNum(contractId)
                                                                .build(),
                                                        Bytecode.newBuilder()
                                                                .code(wrappedContents)
                                                                .build());
                                        if (numBytecodeInsertions.incrementAndGet() % 10_000 == 0) {
                                            // Make sure we are flushing data to disk as we go
                                            ((WritableKVStateBase) bytecodeTs.get()).commit();
                                            ctx.copyAndReleaseOnDiskState(BYTECODE_KEY);
                                            // And ensure we have the latest writable state
                                            bytecodeTs.set(ctx.newStates().get(BYTECODE_KEY));
                                        }
                                        migratedContractNums.add(contractId);
                                    }
                                },
                                1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            log.info(
                    "BBM: finished migrating contract bytecode. Number of migrated contracts: " + migratedContractNums);

            if (bytecodeTs.get().isModified()) ((WritableKVStateBase) bytecodeTs.get()).commit();

            log.info("BBM: contract migration finished");
        } else {
            log.warn("BBM: no contract 'from' state found");
        }

        storageFromState = null;
        contractBytecodeFromState = null;
    }

    @NonNull
    @Override
    @SuppressWarnings("rawtypes")
    public Set<StateDefinition> statesToCreate() {
        return Set.of(storageDef(), bytecodeDef());
    }

    private @NonNull StateDefinition<SlotKey, SlotValue> storageDef() {
        return StateDefinition.onDisk(STORAGE_KEY, SlotKey.PROTOBUF, SlotValue.PROTOBUF, MAX_STORAGE_ENTRIES);
    }

    private @NonNull StateDefinition<ContractID, Bytecode> bytecodeDef() {
        return StateDefinition.onDisk(BYTECODE_KEY, ContractID.PROTOBUF, Bytecode.PROTOBUF, MAX_BYTECODES);
    }
}
