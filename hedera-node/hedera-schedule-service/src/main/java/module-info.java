module com.hedera.node.app.service.schedule {
    requires transitive com.hedera.node.app.spi;
    requires transitive com.swirlds.platform.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires static com.github.spotbugs.annotations;

    exports com.hedera.node.app.service.schedule;

    uses com.hedera.node.app.service.schedule.ScheduleService;
}
