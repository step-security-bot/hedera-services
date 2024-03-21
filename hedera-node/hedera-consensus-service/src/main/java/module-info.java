module com.hedera.node.app.service.consensus {
    exports com.hedera.node.app.service.consensus;

    uses com.hedera.node.app.service.consensus.ConsensusService;

    requires transitive com.hedera.node.app.spi;
    requires transitive com.swirlds.platform.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires static com.github.spotbugs.annotations;
}
