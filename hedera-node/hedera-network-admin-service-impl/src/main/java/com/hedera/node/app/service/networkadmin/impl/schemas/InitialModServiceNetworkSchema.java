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

package com.hedera.node.app.service.networkadmin.impl.schemas;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.platform.state.spi.MigrationContext;
import com.swirlds.platform.state.spi.Schema;
import com.swirlds.platform.state.spi.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * General schema for the network service
 * (FUTURE) When mod-service release is finalized, rename this class to e.g.
 * {@code Release47NetworkSchema} as it will no longer be appropriate to assume
 * this schema is always correct for the current version of the software.
 */
public class InitialModServiceNetworkSchema extends Schema {
    private static final Logger log = LogManager.getLogger(InitialModServiceNetworkSchema.class);

    public InitialModServiceNetworkSchema(@NonNull final SemanticVersion version) {
        super(version);
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of();
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        log.info("BBM: no actions required for network service");
    }
}
