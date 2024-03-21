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

package com.swirlds.platform.state.spi;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;

/**
 * Provided by the application to the com.hedera.node.app.spi.Service, the {@link SchemaRegistry} is used by the
 * {@link Service} to register all of its {@link Schema}s.
 */
public interface SchemaRegistry<T extends Schema> {
    /**
     * Register the given {@link Schema}. {@link Schema}s do not need to be registered in order.
     *
     * @param schema The {@link Schema} to register.
     * @return a reference to this registry instance
     */
    SchemaRegistry register(@NonNull T schema);

    /**
     * Register the given array of {@link Schema}s.
     *
     * @param schemas The schemas to register. Cannot contain nulls or be null.
     * @return a reference to this registry instance
     */
    default SchemaRegistry registerAll(@NonNull T... schemas) {
        for (final var s : schemas) {
            register(s);
        }

        return this;
    }

    /**
     * Register the given array of {@link Schema}s.
     *
     * @param schemas The schemas to register. Cannot contain nulls or be null.
     * @return a reference to this registry instance
     */
    default SchemaRegistry registerAll(@NonNull Collection<T> schemas) {
        for (final var s : schemas) {
            register(s);
        }

        return this;
    }
}
