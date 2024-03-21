module com.hedera.node.app.service.network.admin {
    exports com.hedera.node.app.service.networkadmin;

    uses com.hedera.node.app.service.networkadmin.FreezeService;
    uses com.hedera.node.app.service.networkadmin.NetworkService;

    requires transitive com.hedera.node.app.spi;
    requires transitive com.swirlds.platform.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires static com.github.spotbugs.annotations;
    requires com.swirlds.platform.core;
}
