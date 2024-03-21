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

package com.hedera.node.app;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.state.merkle.MerkleSchemaRegistry;
import com.swirlds.platform.state.merkle.MerkleHederaState;
import com.swirlds.platform.state.spi.Service;
import com.swirlds.platform.state.spi.info.NetworkInfo;
import com.swirlds.platform.state.spi.SchemaRegistry;
import com.hedera.node.config.VersionedConfiguration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Comparator;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The entire purpose of this class is to ensure that inter-service dependencies are respected between
 * migrations. The only required dependency right now is the {@link EntityIdService}, which is needed
 * for genesis blocklist accounts in the token service genesis migration. (See {@link
 * Service#registerSchemas(SchemaRegistry, SemanticVersion)}).
 *
 * <p>Note: there are only two ordering requirements to maintain: first, that the entity ID service
 * is migrated before the token service; and second, that the remaining services are migrated _in any
 * deterministic order_. In order to ensure the entity ID service is migrated before the token service,
 * we'll just migrate the entity ID service first.
 */
public class OrderedServiceMigrator {
    private static final Logger logger = LogManager.getLogger(OrderedServiceMigrator.class);
    private final ServicesRegistry servicesRegistry;

    public OrderedServiceMigrator(@NonNull final ServicesRegistry servicesRegistry) {
        this.servicesRegistry = requireNonNull(servicesRegistry);
    }

    /**
     * Migrates the services registered with the {@link ServicesRegistry}
     */
    public void doMigrations(
            @NonNull final MerkleHederaState state,
            @NonNull final SemanticVersion currentVersion,
            @Nullable final SemanticVersion previousVersion,
            @NonNull final VersionedConfiguration versionedConfiguration,
            @NonNull final NetworkInfo networkInfo) {
        requireNonNull(state);
        requireNonNull(currentVersion);
        requireNonNull(versionedConfiguration);
        requireNonNull(networkInfo);

        logger.info("Migrating Entity ID Service as pre-requisite for other services");
        final var entityIdRegistration = servicesRegistry.registrations().stream()
                .filter(service -> EntityIdService.NAME.equals(service.service().getServiceName()))
                .findFirst()
                .orElseThrow();
        final var entityIdRegistry = (MerkleSchemaRegistry) entityIdRegistration.registry();
        entityIdRegistry.migrate(
                state,
                previousVersion,
                currentVersion,
                versionedConfiguration,
                networkInfo,
                // We call with null here because we're migrating the entity ID service itself
                null);

        // The token service has a dependency on the entity ID service during genesis migrations, so we
        // CAREFULLY create a different WritableStates specific to the entity ID service. The different
        // WritableStates instances won't be able to "see" the changes made by each other, meaning that a
        // change made with WritableStates instance X would _not_ be read by a separate WritableStates
        // instance Y. However, since the inter-service dependencies are limited to the EntityIdService,
        // there shouldn't be any changes made in any single WritableStates instance that would need to be
        // read by any other separate WritableStates instances. This should hold true as long as the
        // EntityIdService is not directly injected into any genesis generation code. Instead, we'll inject
        // this entity ID writable states instance into the MigrationContext below, to enable generation of
        // entity IDs through an appropriate API.
        final var entityIdWritableStates = state.getWritableStates(EntityIdService.NAME);
        final var entityIdStore = new WritableEntityIdStore(entityIdWritableStates);

        // Now that the Entity ID Service is migrated, migrate the remaining services in name order. Note: the name
        // ordering itself isn't important, just that the ordering is deterministic
        servicesRegistry.registrations().stream()
                .filter(r -> !Objects.equals(entityIdRegistration, r))
                .sorted(Comparator.comparing(
                        (ServicesRegistry.Registration r) -> r.service().getServiceName()))
                .forEach(registration -> {
                    // FUTURE We should have metrics here to keep track of how long it takes to
                    // migrate each service
                    final var service = registration.service();
                    final var serviceName = service.getServiceName();
                    logger.info("Migrating Service {}", serviceName);
                    final var registry = (MerkleSchemaRegistry) registration.registry();

                    registry.migrate(
                            state,
                            previousVersion,
                            currentVersion,
                            versionedConfiguration,
                            networkInfo,
                            // If we have reached this point in the code, entityIdStore should not be null because the
                            // EntityIdService should have been migrated already. We enforce with requireNonNull in case
                            // there are scenarios we haven't considered.
                            requireNonNull(entityIdStore));
                    // Now commit any changes that were made to the entity ID state (since other service entities could
                    // depend on newly-generated entity IDs)
                    if (entityIdWritableStates instanceof MerkleHederaState.MerkleWritableStates mws) {
                        mws.commit();
                    }
                });
    }
}
