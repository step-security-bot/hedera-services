module com.swirlds.platform.hapi.test.fixtures {
    exports com.hedera.node.hapi.fixtures;

    requires transitive com.swirlds.platform.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires static com.github.spotbugs.annotations;
}
