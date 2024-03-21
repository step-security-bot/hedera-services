module com.hedera.node.app.service.contract {
    exports com.hedera.node.app.service.contract;

    uses com.hedera.node.app.service.contract.ContractService;

    requires com.swirlds.platform.hapi;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.pbj.runtime;
    requires static com.github.spotbugs.annotations;
}
