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

import com.swirlds.platform.state.spi.ReadableSingletonState;
import com.swirlds.platform.state.spi.ReadableSingletonStateBase;
import com.swirlds.platform.state.spi.WritableSingletonState;
import com.swirlds.platform.state.spi.WritableSingletonStateBase;
import com.swirlds.platform.test.fixtures.state.ListReadableQueueState;
import com.swirlds.platform.test.fixtures.state.ListWritableQueueState;
import com.swirlds.platform.test.fixtures.state.MapReadableKVState;
import com.swirlds.platform.test.fixtures.state.MapReadableStates;
import com.swirlds.platform.test.fixtures.state.MapWritableKVState;
import com.swirlds.platform.test.fixtures.state.MapWritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicReference;

public class StateTestBase extends com.swirlds.platform.test.fixtures.state.StateTestBase {
    @NonNull
    protected MapReadableKVState<String, String> readableFruitState() {
        return MapReadableKVState.<String, String>builder(FRUIT_STATE_KEY)
                .value(A_KEY, APPLE)
                .value(B_KEY, BANANA)
                .value(C_KEY, CHERRY)
                .value(D_KEY, DATE)
                .value(E_KEY, EGGPLANT)
                .value(F_KEY, FIG)
                .value(G_KEY, GRAPE)
                .build();
    }

    @NonNull
    protected MapWritableKVState<String, String> writableFruitState() {
        return MapWritableKVState.<String, String>builder(FRUIT_STATE_KEY)
                .value(A_KEY, APPLE)
                .value(B_KEY, BANANA)
                .value(C_KEY, CHERRY)
                .value(D_KEY, DATE)
                .value(E_KEY, EGGPLANT)
                .value(F_KEY, FIG)
                .value(G_KEY, GRAPE)
                .build();
    }

    @NonNull
    protected MapReadableKVState<String, String> readableAnimalState() {
        return MapReadableKVState.<String, String>builder(ANIMAL_STATE_KEY)
                .value(A_KEY, AARDVARK)
                .value(B_KEY, BEAR)
                .value(C_KEY, CUTTLEFISH)
                .value(D_KEY, DOG)
                .value(E_KEY, EMU)
                .value(F_KEY, FOX)
                .value(G_KEY, GOOSE)
                .build();
    }

    @NonNull
    protected MapWritableKVState<String, String> writableAnimalState() {
        return MapWritableKVState.<String, String>builder(ANIMAL_STATE_KEY)
                .value(A_KEY, AARDVARK)
                .value(B_KEY, BEAR)
                .value(C_KEY, CUTTLEFISH)
                .value(D_KEY, DOG)
                .value(E_KEY, EMU)
                .value(F_KEY, FOX)
                .value(G_KEY, GOOSE)
                .build();
    }

    @NonNull
    protected ReadableSingletonState<String> readableSpaceState() {
        return new ReadableSingletonStateBase<>(SPACE_STATE_KEY, () -> ASTRONAUT);
    }

    @NonNull
    protected WritableSingletonState<String> writableSpaceState() {
        final AtomicReference<String> backingValue = new AtomicReference<>(ASTRONAUT);
        return new WritableSingletonStateBase<>(SPACE_STATE_KEY, backingValue::get, backingValue::set);
    }

    @NonNull
    protected ListReadableQueueState<String> readableSTEAMState() {
        return ListReadableQueueState.<String>builder(STEAM_STATE_KEY)
                .value(ART)
                .value(BIOLOGY)
                .value(CHEMISTRY)
                .value(DISCIPLINE)
                .value(ECOLOGY)
                .value(FIELDS)
                .value(GEOMETRY)
                .build();
    }

    @NonNull
    protected ListWritableQueueState<String> writableSTEAMState() {
        return ListWritableQueueState.<String>builder(STEAM_STATE_KEY)
                .value(ART)
                .value(BIOLOGY)
                .value(CHEMISTRY)
                .value(DISCIPLINE)
                .value(ECOLOGY)
                .value(FIELDS)
                .value(GEOMETRY)
                .build();
    }

    @NonNull
    protected ReadableSingletonState<String> readableCountryState() {
        return new ReadableSingletonStateBase<>(COUNTRY_STATE_KEY, () -> AUSTRALIA);
    }

    @NonNull
    protected WritableSingletonState<String> writableCountryState() {
        final AtomicReference<String> backingValue = new AtomicReference<>(AUSTRALIA);
        return new WritableSingletonStateBase<>(COUNTRY_STATE_KEY, backingValue::get, backingValue::set);
    }

    @NonNull
    protected MapReadableStates allReadableStates() {
        return MapReadableStates.builder()
                .state(readableFruitState())
                .state(readableCountryState())
                .state(readableAnimalState())
                .state(readableSTEAMState())
                .state(readableSpaceState())
                .build();
    }

    @NonNull
    protected MapWritableStates allWritableStates() {
        return MapWritableStates.builder()
                .state(writableAnimalState())
                .state(writableCountryState())
                .state(writableAnimalState())
                .state(writableSTEAMState())
                .state(writableSpaceState())
                .build();
    }
}
