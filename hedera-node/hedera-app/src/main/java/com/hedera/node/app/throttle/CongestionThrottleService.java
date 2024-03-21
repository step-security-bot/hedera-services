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

package com.hedera.node.app.throttle;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.swirlds.platform.state.spi.Schema;
import com.swirlds.platform.state.spi.Service;
import com.swirlds.platform.state.spi.MigrationContext;
import com.swirlds.platform.state.spi.SchemaRegistry;
import com.swirlds.platform.state.spi.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("rawtypes")
@Singleton
public class CongestionThrottleService implements Service {
    private static final Logger log = LogManager.getLogger(CongestionThrottleService.class);
    public static final String NAME = "CongestionThrottleService";
    public static final String THROTTLE_USAGE_SNAPSHOTS_STATE_KEY = "THROTTLE_USAGE_SNAPSHOTS";
    public static final String CONGESTION_LEVEL_STARTS_STATE_KEY = "CONGESTION_LEVEL_STARTS";

    @NonNull
    @Override
    public String getServiceName() {
        return NAME;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry, @NonNull final SemanticVersion version) {
        registry.register(new Schema(version) {
            /** {@inheritDoc} */
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(
                        StateDefinition.singleton(THROTTLE_USAGE_SNAPSHOTS_STATE_KEY, ThrottleUsageSnapshots.PROTOBUF),
                        StateDefinition.singleton(CONGESTION_LEVEL_STARTS_STATE_KEY, CongestionLevelStarts.PROTOBUF));
            }

            /** {@inheritDoc} */
            @Override
            public void migrate(@NonNull final MigrationContext ctx) {
                if (ctx.previousStates().isEmpty()) {
                    // At genesis we put empty throttle usage snapshots and
                    // congestion level starts into their respective singleton
                    // states just to ensure they exist
                    log.info("Creating genesis throttle snapshots and congestion level starts");
                    final var throttleSnapshots = ctx.newStates().getSingleton(THROTTLE_USAGE_SNAPSHOTS_STATE_KEY);
                    throttleSnapshots.put(ThrottleUsageSnapshots.DEFAULT);
                    final var congestionLevelStarts = ctx.newStates().getSingleton(CONGESTION_LEVEL_STARTS_STATE_KEY);
                    congestionLevelStarts.put(CongestionLevelStarts.DEFAULT);
                }
            }
        });
    }
}
