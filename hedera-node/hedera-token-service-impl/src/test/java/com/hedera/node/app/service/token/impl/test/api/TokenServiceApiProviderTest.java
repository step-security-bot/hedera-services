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

package com.hedera.node.app.service.token.impl.test.api;

import static com.hedera.node.app.service.token.impl.api.TokenServiceApiProvider.TOKEN_SERVICE_API_PROVIDER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.token.impl.api.TokenServiceApiImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.spi.WritableStates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenServiceApiProviderTest {
    private static final Configuration DEFAULT_CONFIG = HederaTestConfigBuilder.createConfig();

    @Mock
    private WritableStates writableStates;

    @Test
    void hasTokenServiceName() {
        assertEquals(TokenService.NAME, TOKEN_SERVICE_API_PROVIDER.serviceName());
    }

    @Test
    void instantiatesApiImpl() {
        assertInstanceOf(
                TokenServiceApiImpl.class, TOKEN_SERVICE_API_PROVIDER.newInstance(DEFAULT_CONFIG, writableStates));
    }

    @Test
    void testsCustomFeesByCreatingStep() {
        final var api = TOKEN_SERVICE_API_PROVIDER.newInstance(DEFAULT_CONFIG, writableStates);
        assertFalse(api.checkForCustomFees(CryptoTransferTransactionBody.DEFAULT));
    }

    @Test
    void returnsFalseOnAnyStepCreationFailure() {
        given(writableStates.get(any())).willReturn(null);
        given(writableStates.get(TokenServiceImpl.TOKEN_RELS_KEY)).willThrow(IllegalStateException.class);
        final var api = TOKEN_SERVICE_API_PROVIDER.newInstance(DEFAULT_CONFIG, writableStates);
        assertFalse(api.checkForCustomFees(CryptoTransferTransactionBody.DEFAULT));
    }
}
