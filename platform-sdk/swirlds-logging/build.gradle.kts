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

plugins {
    id("com.hedera.hashgraph.sdk.conventions")
    id("com.hedera.hashgraph.platform-maven-publish")
    id("com.hedera.hashgraph.java-test-fixtures")
    id("com.hedera.hashgraph.benchmark-conventions")
}

mainModuleInfo { annotationProcessor("com.google.auto.service.processor") }

testModuleInfo {
    requires("org.apache.logging.log4j.core")
    requires("com.swirlds.config.extensions.test.fixtures")
    requires("org.assertj.core")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requires("com.swirlds.base.test.fixtures")
    requires("com.swirlds.common.test.fixtures")
    requires("jakarta.inject")
}

jmhModuleInfo {
    requires("com.swirlds.logging")
    requires("org.apache.logging.log4j")
    requires("com.swirlds.config.api")
    runtimeOnly("com.swirlds.config.impl")
    requires("com.swirlds.config.extensions")
    requires("org.apache.logging.log4j.core")
    requires("com.github.spotbugs.annotations")
    requires("jmh.core")
}
