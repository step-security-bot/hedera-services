/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.merkle.singleton;

import com.swirlds.platform.state.merkle.StateMetadata;
import com.swirlds.platform.state.spi.WritableSingletonStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;

public class WritableSingletonStateImpl<T> extends WritableSingletonStateBase<T> {
    public WritableSingletonStateImpl(@NonNull final StateMetadata<?, ?> md, @NonNull final SingletonNode<T> node) {
        super(md.stateDefinition().stateKey(), node::getValue, node::setValue);
    }
}
