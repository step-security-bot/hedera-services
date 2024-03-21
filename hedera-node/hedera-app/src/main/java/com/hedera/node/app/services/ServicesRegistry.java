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

package com.hedera.node.app.services;

import static java.util.Objects.requireNonNull;

import com.swirlds.platform.state.spi.Service;
import com.swirlds.platform.state.spi.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import javax.inject.Singleton;

/**
 * A registry providing access to all services registered with the application.
 */
@Singleton
public interface ServicesRegistry {
    /**
     * A record of a service registration.
     *
     * @param service The service that was registered
     * @param registry The schema registry for the service
     */
    record Registration(@NonNull Service service, @NonNull SchemaRegistry registry) {
        public Registration {
            requireNonNull(service);
            requireNonNull(registry);
        }
    }

    /**
     * Gets the full set of services registered, sorted deterministically.
     *
     * @return The set of services. May be empty.
     */
    @NonNull
    Set<Registration> registrations();
}
