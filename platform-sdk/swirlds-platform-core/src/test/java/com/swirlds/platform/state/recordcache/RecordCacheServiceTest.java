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

package com.hedera.node.app.state.recordcache;

import static com.hedera.node.app.spi.fixtures.state.TestSchema.CURRENT_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.swirlds.platform.state.spi.Schema;
import com.swirlds.platform.state.spi.SchemaRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class RecordCacheServiceTest {
    @Captor
    ArgumentCaptor<Schema> captor;

    @Test
    void constructor() {
        final var svc = new RecordCacheService();
        assertThat(svc.getServiceName()).isEqualTo(RecordCacheService.NAME);
    }

    @Test
    void schema(@Mock final SchemaRegistry registry) {
        final var svc = new RecordCacheService();
        svc.registerSchemas(registry, CURRENT_VERSION);
        verify(registry).register(captor.capture());
        final var schema = captor.getValue();
        assertThat(schema.getVersion()).isEqualTo(CURRENT_VERSION);
        assertThat(schema.statesToCreate()).hasSize(1);
    }
}
