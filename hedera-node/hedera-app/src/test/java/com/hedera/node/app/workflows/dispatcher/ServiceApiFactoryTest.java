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

package com.hedera.node.app.workflows.dispatcher;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.spi.WritableStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceApiFactoryTest {
    private static final Configuration DEFAULT_CONFIG = HederaTestConfigBuilder.createConfig();

    @Mock
    private WritableStates writableStates;

    @Mock
    private SavepointStackImpl stack;

    private ServiceApiFactory subject;

    @BeforeEach
    void setUp() {
        subject = new ServiceApiFactory(stack, DEFAULT_CONFIG);
    }

    @Test
    void throwsIfNoSuchProvider() {
        assertThrows(IllegalArgumentException.class, () -> subject.getApi(NonExistentApi.class));
    }

    @Test
    void canCreateTokenServiceApi() {
        given(stack.getWritableStates(TokenService.NAME)).willReturn(writableStates);
        assertNotNull(subject.getApi(TokenServiceApi.class));
    }

    private static class NonExistentApi {}
}
