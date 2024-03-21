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

package com.hedera.node.app.service.file.impl.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.swirlds.platform.state.spi.Schema;
import com.swirlds.platform.state.spi.SchemaRegistry;
import com.hedera.node.config.ConfigProvider;
import com.swirlds.platform.state.spi.StateDefinition;
import com.swirlds.platform.test.fixtures.state.TestSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {
    @Mock
    private SchemaRegistry registry;

    private ConfigProvider configProvider;

    @BeforeEach
    void setUp() {
        configProvider = new BootstrapConfigProviderImpl();
    }

    @Test
    void registersExpectedSchema() {
        ArgumentCaptor<Schema> schemaCaptor = ArgumentCaptor.forClass(Schema.class);

        subject().registerSchemas(registry, TestSchema.CURRENT_VERSION);

        verify(registry).register(schemaCaptor.capture());

        final var schema = schemaCaptor.getValue();

        final var statesToCreate = schema.statesToCreate();
        assertEquals(11, statesToCreate.size());
        final var iter =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().iterator();
        assertEquals(FileServiceImpl.BLOBS_KEY, iter.next());
    }

    private FileService subject() {
        return new FileServiceImpl(configProvider);
    }
}
