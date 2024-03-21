module com.hedera.node.app.service.file {
    exports com.hedera.node.app.service.file;

    uses com.hedera.node.app.service.file.FileService;

    requires transitive com.swirlds.platform.core;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.swirlds.platform.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires static com.github.spotbugs.annotations;
}
