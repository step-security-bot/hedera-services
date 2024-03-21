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

package com.hedera.node.app.workflows.handle.stack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import com.swirlds.platform.state.HederaState;
import com.swirlds.platform.state.spi.ReadableStates;
import com.swirlds.platform.test.fixtures.state.MapWritableKVState;
import com.swirlds.platform.test.fixtures.state.MapWritableStates;
import com.swirlds.platform.test.fixtures.state.StateTestBase;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavepointStackImplTest extends StateTestBase {

    private static final String FOOD_SERVICE = "FOOD_SERVICE";

    private final Map<String, String> BASE_DATA = Map.of(
            A_KEY, APPLE,
            B_KEY, BANANA,
            C_KEY, CHERRY,
            D_KEY, DATE,
            E_KEY, EGGPLANT,
            F_KEY, FIG,
            G_KEY, GRAPE);

    @Mock(strictness = LENIENT)
    private HederaState baseState;

    @BeforeEach
    void setup() {
        final var baseKVState = new MapWritableKVState<>(FRUIT_STATE_KEY, new HashMap<>(BASE_DATA));
        final var writableStates =
                MapWritableStates.builder().state(baseKVState).build();
        when(baseState.getReadableStates(FOOD_SERVICE)).thenReturn(writableStates);
        when(baseState.getWritableStates(FOOD_SERVICE)).thenReturn(writableStates);
    }

    @Test
    void testConstructor() {
        // when
        final var stack = new SavepointStackImpl(baseState);

        // then
        assertThat(stack.depth()).isEqualTo(1);
        assertThat(stack.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.rootStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.peek().getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.peek().getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithInvalidParameters() {
        assertThatThrownBy(() -> new SavepointStackImpl(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testModification() {
        // given
        final var stack = new SavepointStackImpl(baseState);
        final var readableStatesStack = stack.getReadableStates(FOOD_SERVICE);
        final var writableStatesStack = stack.getWritableStates(FOOD_SERVICE);

        // when
        writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
        stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(B_KEY, BLUEBERRY);

        // then
        assertThat(stack.depth()).isEqualTo(1);
        final var newData = new HashMap<>(BASE_DATA);
        newData.put(A_KEY, ACAI);
        newData.put(B_KEY, BLUEBERRY);
        assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.rootStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.getReadableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.getWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
        assertThat(readableStatesStack).has(content(newData));
        assertThat(writableStatesStack).has(content(newData));
        assertThat(stack.peek().getReadableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.peek().getWritableStates(FOOD_SERVICE)).has(content(newData));
    }

    @Nested
    @DisplayName("Tests for adding new savepoints to the stack")
    class SavepointTests {
        @Test
        void testInitialCreatedSavepoint() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            final var readableStatesStack = stack.getReadableStates(FOOD_SERVICE);
            final var writableStatesStack = stack.getWritableStates(FOOD_SERVICE);

            // when
            stack.createSavepoint();

            // then
            assertThat(stack.depth()).isEqualTo(2);
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.rootStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.getWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
            assertThat(readableStatesStack).has(content(BASE_DATA));
            assertThat(writableStatesStack).has(content(BASE_DATA));
            assertThat(stack.peek().getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.peek().getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        }

        @Test
        void testModifiedSavepoint() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            final var readableStatesStack = stack.getReadableStates(FOOD_SERVICE);
            final var writableStatesStack = stack.getWritableStates(FOOD_SERVICE);

            // when
            writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(B_KEY, BLUEBERRY);
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(C_KEY, CRANBERRY);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(D_KEY, DRAGONFRUIT);

            // then
            assertThat(stack.depth()).isEqualTo(2);
            final var newData = new HashMap<>(BASE_DATA);
            newData.put(A_KEY, ACAI);
            newData.put(B_KEY, BLUEBERRY);
            newData.put(C_KEY, CRANBERRY);
            newData.put(D_KEY, DRAGONFRUIT);
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.rootStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.getWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
            assertThat(readableStatesStack).has(content(newData));
            assertThat(writableStatesStack).has(content(newData));
            assertThat(stack.peek().getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.peek().getWritableStates(FOOD_SERVICE)).has(content(newData));
        }

        @Test
        void testMultipleSavepoints() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            final var readableStatesStack = stack.getReadableStates(FOOD_SERVICE);
            final var writableStatesStack = stack.getWritableStates(FOOD_SERVICE);

            // when
            writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(B_KEY, BLUEBERRY);
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(C_KEY, CRANBERRY);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(D_KEY, DRAGONFRUIT);
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(E_KEY, ELDERBERRY);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(F_KEY, FEIJOA);

            // then
            assertThat(stack.depth()).isEqualTo(3);
            final var newData = new HashMap<>(BASE_DATA);
            newData.put(A_KEY, ACAI);
            newData.put(B_KEY, BLUEBERRY);
            newData.put(C_KEY, CRANBERRY);
            newData.put(D_KEY, DRAGONFRUIT);
            newData.put(E_KEY, ELDERBERRY);
            newData.put(F_KEY, FEIJOA);
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.rootStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.getWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
            assertThat(readableStatesStack).has(content(newData));
            assertThat(writableStatesStack).has(content(newData));
            assertThat(stack.peek().getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.peek().getWritableStates(FOOD_SERVICE)).has(content(newData));
        }
    }

    @Nested
    @DisplayName("Test for committing savepoints")
    class CommitTests {
        @Test
        void testCommittedSavepoint() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            final var readableStatesStack = stack.getReadableStates(FOOD_SERVICE);
            final var writableStatesStack = stack.getWritableStates(FOOD_SERVICE);
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(B_KEY, BLUEBERRY);

            // when
            stack.commit();

            // then
            assertThat(stack.depth()).isEqualTo(1);
            final var newData = new HashMap<>(BASE_DATA);
            newData.put(A_KEY, ACAI);
            newData.put(B_KEY, BLUEBERRY);
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.rootStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.getWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
            assertThat(readableStatesStack).has(content(newData));
            assertThat(writableStatesStack).has(content(newData));
            assertThat(stack.peek().getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.peek().getWritableStates(FOOD_SERVICE)).has(content(newData));
        }

        @Test
        void testModificationsAfterCommit() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            final var readableStatesStack = stack.getReadableStates(FOOD_SERVICE);
            final var writableStatesStack = stack.getWritableStates(FOOD_SERVICE);
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(B_KEY, BLUEBERRY);
            stack.commit();

            // when
            writableStatesStack.get(FRUIT_STATE_KEY).put(C_KEY, CRANBERRY);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(D_KEY, DRAGONFRUIT);

            // then
            assertThat(stack.depth()).isEqualTo(1);
            final var newData = new HashMap<>(BASE_DATA);
            newData.put(A_KEY, ACAI);
            newData.put(B_KEY, BLUEBERRY);
            newData.put(C_KEY, CRANBERRY);
            newData.put(D_KEY, DRAGONFRUIT);
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.rootStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.getWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
            assertThat(readableStatesStack).has(content(newData));
            assertThat(writableStatesStack).has(content(newData));
            assertThat(stack.peek().getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.peek().getWritableStates(FOOD_SERVICE)).has(content(newData));
        }

        @Test
        void testNewSavepointAfterCommit() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            final var readableStatesStack = stack.getReadableStates(FOOD_SERVICE);
            final var writableStatesStack = stack.getWritableStates(FOOD_SERVICE);
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(B_KEY, BLUEBERRY);
            stack.commit();

            // when
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(C_KEY, CRANBERRY);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(D_KEY, DRAGONFRUIT);

            // then
            assertThat(stack.depth()).isEqualTo(2);
            final var newData = new HashMap<>(BASE_DATA);
            newData.put(A_KEY, ACAI);
            newData.put(B_KEY, BLUEBERRY);
            newData.put(C_KEY, CRANBERRY);
            newData.put(D_KEY, DRAGONFRUIT);
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.rootStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.getWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
            assertThat(readableStatesStack).has(content(newData));
            assertThat(writableStatesStack).has(content(newData));
            assertThat(stack.peek().getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.peek().getWritableStates(FOOD_SERVICE)).has(content(newData));
        }

        @Test
        void testMultipleCommits() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            final var readableStatesStack = stack.getReadableStates(FOOD_SERVICE);
            final var writableStatesStack = stack.getWritableStates(FOOD_SERVICE);
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(B_KEY, BLUEBERRY);
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(C_KEY, CRANBERRY);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(D_KEY, DRAGONFRUIT);
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(E_KEY, ELDERBERRY);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(F_KEY, FEIJOA);

            // when
            stack.commit();
            stack.commit();

            // then
            assertThat(stack.depth()).isEqualTo(2);
            final var newData = new HashMap<>(BASE_DATA);
            newData.put(A_KEY, ACAI);
            newData.put(B_KEY, BLUEBERRY);
            newData.put(C_KEY, CRANBERRY);
            newData.put(D_KEY, DRAGONFRUIT);
            newData.put(E_KEY, ELDERBERRY);
            newData.put(F_KEY, FEIJOA);
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.rootStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.getWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
            assertThat(readableStatesStack).has(content(newData));
            assertThat(writableStatesStack).has(content(newData));
            assertThat(stack.peek().getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.peek().getWritableStates(FOOD_SERVICE)).has(content(newData));
        }

        @Test
        void testCommitInitialStackFails() {
            // given
            final var stack = new SavepointStackImpl(baseState);

            // then
            assertThatThrownBy(stack::commit).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void testTooManyCommitsFail() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            stack.createSavepoint();
            stack.createSavepoint();

            // then
            assertThatCode(stack::commit).doesNotThrowAnyException();
            assertThatCode(stack::commit).doesNotThrowAnyException();
            assertThatThrownBy(stack::commit).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Test for rolling back savepoints")
    class RollbackTests {
        @Test
        void testRolledBackSavepoint() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            final var readableStatesStack = stack.getReadableStates(FOOD_SERVICE);
            final var writableStatesStack = stack.getWritableStates(FOOD_SERVICE);
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(B_KEY, BLUEBERRY);

            // when
            stack.rollback();

            // then
            assertThat(stack.depth()).isEqualTo(1);
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.rootStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.getWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
            assertThat(readableStatesStack).has(content(BASE_DATA));
            assertThat(writableStatesStack).has(content(BASE_DATA));
            assertThat(stack.peek().getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.peek().getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        }

        @Test
        void testModificationsAfterRollback() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            final var readableStatesStack = stack.getReadableStates(FOOD_SERVICE);
            final var writableStatesStack = stack.getWritableStates(FOOD_SERVICE);
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(B_KEY, BLUEBERRY);
            stack.rollback();

            // when
            writableStatesStack.get(FRUIT_STATE_KEY).put(C_KEY, CRANBERRY);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(D_KEY, DRAGONFRUIT);

            // then
            assertThat(stack.depth()).isEqualTo(1);
            final var newData = new HashMap<>(BASE_DATA);
            newData.put(C_KEY, CRANBERRY);
            newData.put(D_KEY, DRAGONFRUIT);
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.rootStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.getWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
            assertThat(readableStatesStack).has(content(newData));
            assertThat(writableStatesStack).has(content(newData));
            assertThat(stack.peek().getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.peek().getWritableStates(FOOD_SERVICE)).has(content(newData));
        }

        @Test
        void testNewSavepointAfterRollback() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            final var readableStatesStack = stack.getReadableStates(FOOD_SERVICE);
            final var writableStatesStack = stack.getWritableStates(FOOD_SERVICE);
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(B_KEY, BLUEBERRY);
            stack.rollback();

            // when
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(C_KEY, CRANBERRY);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(D_KEY, DRAGONFRUIT);

            // then
            assertThat(stack.depth()).isEqualTo(2);
            final var newData = new HashMap<>(BASE_DATA);
            newData.put(C_KEY, CRANBERRY);
            newData.put(D_KEY, DRAGONFRUIT);
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.rootStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.getWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
            assertThat(readableStatesStack).has(content(newData));
            assertThat(writableStatesStack).has(content(newData));
            assertThat(stack.peek().getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.peek().getWritableStates(FOOD_SERVICE)).has(content(newData));
        }

        @Test
        void testMultipleRollbacks() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            final var readableStatesStack = stack.getReadableStates(FOOD_SERVICE);
            final var writableStatesStack = stack.getWritableStates(FOOD_SERVICE);
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(B_KEY, BLUEBERRY);
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(C_KEY, CRANBERRY);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(D_KEY, DRAGONFRUIT);
            stack.createSavepoint();
            writableStatesStack.get(FRUIT_STATE_KEY).put(E_KEY, ELDERBERRY);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(F_KEY, FEIJOA);

            // when
            stack.rollback();
            stack.rollback();

            // then
            assertThat(stack.depth()).isEqualTo(2);
            final var newData = new HashMap<>(BASE_DATA);
            newData.put(A_KEY, ACAI);
            newData.put(B_KEY, BLUEBERRY);
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.rootStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(stack.getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.getWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
            assertThat(readableStatesStack).has(content(newData));
            assertThat(writableStatesStack).has(content(newData));
            assertThat(stack.peek().getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.peek().getWritableStates(FOOD_SERVICE)).has(content(newData));
        }

        @Test
        void testRollbackInitialStackFails() {
            // given
            final var stack = new SavepointStackImpl(baseState);

            // then
            assertThatThrownBy(stack::rollback).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void testTooManyRollbacksFail() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            stack.createSavepoint();
            stack.createSavepoint();

            // then
            assertThatCode(stack::rollback).doesNotThrowAnyException();
            assertThatCode(stack::rollback).doesNotThrowAnyException();
            assertThatThrownBy(stack::rollback).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Tests for committing the full stack")
    class FullStackCommitTests {
        @Test
        void testCommitFullStack() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            final var writableState = stack.getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY);
            writableState.put(A_KEY, ACAI);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(B_KEY, BLUEBERRY);
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));

            // when
            stack.commitFullStack();

            // then
            final var newData = new HashMap<>(BASE_DATA);
            newData.put(A_KEY, ACAI);
            newData.put(B_KEY, BLUEBERRY);
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(newData));
        }

        @Test
        void testCommitFullStackAfterSingleCommit() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            stack.createSavepoint();
            final var writableState = stack.getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY);
            writableState.put(A_KEY, ACAI);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(B_KEY, BLUEBERRY);
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));

            // when
            stack.commit();
            stack.commitFullStack();

            // then
            final var newData = new HashMap<>(BASE_DATA);
            newData.put(A_KEY, ACAI);
            newData.put(B_KEY, BLUEBERRY);
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(newData));
        }

        @Test
        void testCommitFullStackAfterRollback() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            stack.createSavepoint();
            final var writableState = stack.getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY);
            writableState.put(A_KEY, ACAI);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(B_KEY, BLUEBERRY);
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));

            // when
            stack.rollback();
            stack.commitFullStack();

            // then
            assertThat(baseState.getReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
            assertThat(baseState.getWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        }

        @Test
        void testStackAfterCommitFullStack() {
            // given
            final var stack = new SavepointStackImpl(baseState);

            // when
            stack.commitFullStack();

            // then
            assertThatThrownBy(stack::commit).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(stack::rollback).isInstanceOf(IllegalStateException.class);
            assertThat(stack.depth()).isOne();
            assertThatCode(stack::commitFullStack).doesNotThrowAnyException();
            assertThatCode(stack::createSavepoint).doesNotThrowAnyException();
        }

        @Test
        void testReuseAfterCommitFullStack() {
            // given
            final var stack = new SavepointStackImpl(baseState);
            final var writableState = stack.getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY);
            writableState.put(A_KEY, ACAI);
            final var newData = new HashMap<>(BASE_DATA);
            newData.put(A_KEY, ACAI);

            // when
            stack.commitFullStack();

            // then
            assertThat(stack.getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.getWritableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.rootStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.peek().getReadableStates(FOOD_SERVICE)).has(content(newData));
            assertThat(stack.peek().getWritableStates(FOOD_SERVICE)).has(content(newData));
        }
    }

    private static Condition<ReadableStates> content(Map<String, String> expected) {
        return new Condition<>(contentCheck(expected), "state " + expected);
    }

    private static Predicate<ReadableStates> contentCheck(Map<String, String> expected) {
        return readableStates -> {
            final var actual = readableStates.get(FRUIT_STATE_KEY);
            if (expected.size() != actual.size()) {
                return false;
            }
            for (final var entry : expected.entrySet()) {
                if (!Objects.equals(entry.getValue(), actual.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        };
    }
}
