module com.hedera.node.app.spi {
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.config.api;
    requires static com.github.spotbugs.annotations;
    requires com.swirlds.platform.core;
    requires com.swirlds.platform.hapi;

    exports com.hedera.node.app.spi;
    exports com.hedera.node.app.spi.fees;
    exports com.hedera.node.app.spi.api;
    exports com.hedera.node.app.spi.key;
    exports com.hedera.node.app.spi.numbers;
    exports com.hedera.node.app.spi.workflows;
    exports com.hedera.node.app.spi.records;
    exports com.hedera.node.app.spi.signatures;
    exports com.hedera.node.app.spi.validation;
    exports com.hedera.node.app.spi.workflows.record;
    exports com.hedera.node.app.spi.authorization;
}
