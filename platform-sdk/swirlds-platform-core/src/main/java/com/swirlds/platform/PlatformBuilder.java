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

package com.swirlds.platform;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.io.utility.FileUtils.rethrowIO;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.platform.StaticPlatformBuilder.doStaticSetup;
import static com.swirlds.platform.StaticPlatformBuilder.getGlobalMetrics;
import static com.swirlds.platform.StaticPlatformBuilder.getMetricsProvider;
import static com.swirlds.platform.crypto.CryptoStatic.initNodeSecurity;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.getPlatforms;
import static com.swirlds.platform.state.signed.StartupStateUtils.getInitialState;
import static com.swirlds.platform.util.BootstrapUtils.checkNodesToRun;
import static com.swirlds.platform.util.BootstrapUtils.detectSoftwareUpgrade;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyFactory;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.io.utility.RecycleBinImpl;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.merkle.crypto.MerkleCryptographyFactory;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.config.BasicConfig;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.config.internal.PlatformConfigUtils;
import com.swirlds.platform.config.legacy.LegacyConfigProperties;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.SignedStateLoadingException;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.address.AddressBookInitializer;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.util.BootstrapUtils;
import com.swirlds.platform.util.MetricsDocUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Builds a {@link SwirldsPlatform} instance.
 */
public final class PlatformBuilder {

    private final String appName;
    private final SoftwareVersion softwareVersion;
    private final Supplier<SwirldState> genesisStateBuilder;
    private final NodeId selfId;
    private final String swirldName;

    private ConfigurationBuilder configurationBuilder;

    private static final String SWIRLDS_PACKAGE = "com.swirlds";

    public static final String DEFAULT_CONFIG_FILE_NAME = "config.txt";
    public static final String DEFAULT_SETTINGS_FILE_NAME = "settings.txt";

    /**
     * The path to the configuration file (i.e. the file with the address book).
     */
    private Path configPath = getAbsolutePath(DEFAULT_CONFIG_FILE_NAME);

    /**
     * The path to the settings file (i.e. the path used to instantiate {@link Configuration}).
     */
    private Path settingsPath = getAbsolutePath(DEFAULT_SETTINGS_FILE_NAME);

    private Consumer<GossipEvent> preconsensusEventConsumer;
    private Consumer<ConsensusSnapshot> snapshotOverrideConsumer;

    /**
     * Create a new platform builder.
     *
     * @param appName             the name of the application, currently used for deciding where to store states on
     *                            disk
     * @param swirldName          the name of the swirld, currently used for deciding where to store states on disk
     * @param selfId              the ID of this node
     * @param softwareVersion     the software version of the application
     * @param genesisStateBuilder a supplier that will be called to create the genesis state, if necessary
     */
    public PlatformBuilder(
            @NonNull final String appName,
            @NonNull final String swirldName,
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final Supplier<SwirldState> genesisStateBuilder,
            @NonNull final NodeId selfId) {

        this.appName = Objects.requireNonNull(appName);
        this.swirldName = Objects.requireNonNull(swirldName);
        this.softwareVersion = Objects.requireNonNull(softwareVersion);
        this.genesisStateBuilder = Objects.requireNonNull(genesisStateBuilder);
        this.selfId = Objects.requireNonNull(selfId);

        StaticSoftwareVersion.setSoftwareVersion(softwareVersion);
    }

    /**
     * Set the configuration builder to use. If not provided then one is generated when the platform is built.
     *
     * @param configurationBuilder the configuration builder to use
     * @return this
     */
    @NonNull
    public PlatformBuilder withConfigurationBuilder(@Nullable final ConfigurationBuilder configurationBuilder) {
        this.configurationBuilder = configurationBuilder;
        return this;
    }

    /**
     * Set the path to the settings file (i.e. the file used to instantiate {@link Configuration}). Traditionally named
     * {@link #DEFAULT_SETTINGS_FILE_NAME}.
     *
     * @param path the path to the settings file
     * @return this
     */
    @NonNull
    public PlatformBuilder withSettingsPath(@NonNull final Path path) {
        Objects.requireNonNull(path);
        this.settingsPath = getAbsolutePath(path);
        return this;
    }

    /**
     * The path to the config file (i.e. the file with the address book. Traditionally named
     * {@link #DEFAULT_CONFIG_FILE_NAME}.
     *
     * @param path the path to the config file
     * @return this
     */
    @NonNull
    public PlatformBuilder withConfigPath(@NonNull final Path path) {
        Objects.requireNonNull(path);
        this.configPath = getAbsolutePath(path);
        return this;
    }

    /**
     * Provide the platform with the class ID of the previous software version. Needed at migration boundaries if the
     * class ID of the software version has changed.
     *
     * @param previousSoftwareVersionClassId the class ID of the previous software version
     * @return this
     */
    @NonNull
    public PlatformBuilder withPreviousSoftwareVersionClassId(final long previousSoftwareVersionClassId) {
        final Set<Long> softwareVersions = new HashSet<>();
        softwareVersions.add(softwareVersion.getClassId());
        softwareVersions.add(previousSoftwareVersionClassId);
        StaticSoftwareVersion.setSoftwareVersion(softwareVersions);
        return this;
    }

    /**
     * Registers a callback that is called for each valid non-ancient preconsensus event in topological order (i.e.
     * after each event exits the orphan buffer). Useful for scenarios where access to this internal stream of events is
     * useful (e.g. UI hashgraph visualizers).
     *
     * <p>
     * Among all callbacks in the following list, it is guaranteed that callbacks will not be called concurrently, and
     * that there will be a happens-before relationship between each of the callbacks.
     *
     * <ul>
     *     <li>{@link #withPreconsensusEventCallback(Consumer)} (i.e. this callback)</li>
     *     <li>{@link #withConsensusSnapshotOverrideCallback(Consumer)}</li>
     * </ul>
     *
     * @param preconsensusEventConsumer the callback to register
     * @return this
     */
    @NonNull
    public PlatformBuilder withPreconsensusEventCallback(
            @NonNull final Consumer<GossipEvent> preconsensusEventConsumer) {
        this.preconsensusEventConsumer = Objects.requireNonNull(preconsensusEventConsumer);
        return this;
    }

    /**
     * Registers a callback that is called when the consensus snapshot is specified by an out of band operation (i.e.
     * restart or reconnect). Useful for scenarios where access to this internal stream of data is useful (e.g. UI
     * hashgraph visualizers).
     *
     * <p>
     * Among all callbacks in the following list, it is guaranteed that callbacks will not be called concurrently, and
     * that there will be a happens-before relationship between each of the callbacks.
     *
     * <ul>
     *     <li>{@link #withPreconsensusEventCallback(Consumer)}</li>
     *     <li>{@link #withConsensusSnapshotOverrideCallback(Consumer)} (i.e. this callback)</li>
     * </ul>
     *
     * @return
     */
    @NonNull
    public PlatformBuilder withConsensusSnapshotOverrideCallback(
            @NonNull final Consumer<ConsensusSnapshot> snapshotOverrideConsumer) {
        this.snapshotOverrideConsumer = Objects.requireNonNull(snapshotOverrideConsumer);
        return this;
    }

    /**
     * Build the configuration for the node.
     *
     * @return the configuration
     */
    @NonNull
    private Configuration buildConfiguration() {
        if (configurationBuilder == null) {
            configurationBuilder = ConfigurationBuilder.create();
        }

        rethrowIO(() -> BootstrapUtils.setupConfigBuilder(configurationBuilder, settingsPath));

        final Configuration configuration = configurationBuilder.build();
        PlatformConfigUtils.checkConfiguration(configuration);

        return configuration;
    }

    /**
     * Parse the address book from the config.txt file.
     *
     * @return the address book
     */
    @NonNull
    private AddressBook loadConfigAddressBook() {
        final LegacyConfigProperties legacyConfig = LegacyConfigPropertiesLoader.loadConfigFile(configPath);
        legacyConfig.appConfig().ifPresent(c -> ParameterProvider.getInstance().setParameters(c.params()));
        return legacyConfig.getAddressBook();
    }

    /**
     * Build a platform. Platform is not started.
     *
     * @return a new platform instance
     */
    @NonNull
    public Platform build() {
        final Configuration configuration = buildConfiguration();

        final Cryptography cryptography = CryptographyFactory.create(configuration);
        final MerkleCryptography merkleCryptography = MerkleCryptographyFactory.create(configuration, cryptography);

        // For backwards compatibility with the old static access pattern.
        CryptographyHolder.set(cryptography);
        MerkleCryptoFactory.set(merkleCryptography);

        final boolean firstTimeSetup = doStaticSetup(configuration, configPath);

        final AddressBook configAddressBook = loadConfigAddressBook();

        checkNodesToRun(List.of(selfId));

        final Map<NodeId, KeysAndCerts> keysAndCerts = initNodeSecurity(configAddressBook, configuration);
        final PlatformContext platformContext = new DefaultPlatformContext(
                configuration, getMetricsProvider().createPlatformMetrics(selfId), cryptography, Time.getCurrent());

        // the AddressBook is not changed after this point, so we calculate the hash now
        platformContext.getCryptography().digestSync(configAddressBook);

        final RecycleBinImpl recycleBin = rethrowIO(() -> new RecycleBinImpl(
                configuration, platformContext.getMetrics(), getStaticThreadManager(), Time.getCurrent(), selfId));

        // We can't send a "real" dispatch, since the dispatcher will not have been started by the
        // time this class is used.
        final BasicConfig basicConfig = configuration.getConfigData(BasicConfig.class);
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        final EmergencyRecoveryManager emergencyRecoveryManager =
                new EmergencyRecoveryManager(stateConfig, basicConfig.getEmergencyRecoveryFileLoadDir());

        try (final ReservedSignedState initialState = getInitialState(
                platformContext,
                recycleBin,
                softwareVersion,
                genesisStateBuilder,
                appName,
                swirldName,
                selfId,
                configAddressBook,
                emergencyRecoveryManager)) {

            final boolean softwareUpgrade = detectSoftwareUpgrade(softwareVersion, initialState.get());

            // Initialize the address book from the configuration and platform saved state.
            final AddressBookInitializer addressBookInitializer = new AddressBookInitializer(
                    selfId,
                    softwareVersion,
                    softwareUpgrade,
                    initialState.get(),
                    configAddressBook.copy(),
                    platformContext);

            if (addressBookInitializer.hasAddressBookChanged()) {
                final State state = initialState.get().getState();
                // Update the address book with the current address book read from config.txt.
                // Eventually we will not do this, and only transactions will be capable of
                // modifying the address book.
                state.getPlatformState()
                        .setAddressBook(
                                addressBookInitializer.getCurrentAddressBook().copy());

                state.getPlatformState()
                        .setPreviousAddressBook(
                                addressBookInitializer.getPreviousAddressBook() == null
                                        ? null
                                        : addressBookInitializer
                                                .getPreviousAddressBook()
                                                .copy());
            }

            // At this point the initial state must have the current address book set.  If not, something is wrong.
            if (initialState.get().getState().getPlatformState().getAddressBook() == null) {
                throw new IllegalStateException("The current address book of the initial state is null.");
            }

            final SwirldsPlatform platform = new SwirldsPlatform(
                    platformContext,
                    keysAndCerts.get(selfId),
                    recycleBin,
                    selfId,
                    appName,
                    swirldName,
                    softwareVersion,
                    initialState.get(),
                    emergencyRecoveryManager,
                    preconsensusEventConsumer,
                    snapshotOverrideConsumer);

            if (firstTimeSetup) {
                MetricsDocUtils.writeMetricsDocumentToFile(getGlobalMetrics(), getPlatforms(), configuration);
                getMetricsProvider().start();
            }

            return platform;
        } catch (final SignedStateLoadingException e) {
            throw new RuntimeException("unable to load state from disk", e);
        }
    }
}
