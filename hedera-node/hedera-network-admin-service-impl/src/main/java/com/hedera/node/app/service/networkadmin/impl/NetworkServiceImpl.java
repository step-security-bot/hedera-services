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

package com.hedera.node.app.service.networkadmin.impl;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.networkadmin.NetworkService;
import com.hedera.node.app.service.networkadmin.impl.schemas.InitialModServiceNetworkSchema;
import com.swirlds.platform.state.spi.Schema;
import com.swirlds.platform.state.spi.SchemaRegistry;
import com.swirlds.platform.state.spi.Service;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Standard implementation of the {@link NetworkService} {@link Service}.
 */
public final class NetworkServiceImpl implements NetworkService {

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry, @NonNull final SemanticVersion version) {
        registry.register(networkSchema(version));
    }

    private Schema networkSchema(@NonNull final SemanticVersion version) {
        return new InitialModServiceNetworkSchema(version);
    }
}
