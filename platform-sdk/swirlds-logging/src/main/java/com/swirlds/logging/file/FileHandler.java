/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.logging.file;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.handler.AbstractSyncedHandler;
import com.swirlds.logging.api.internal.format.LineBasedFormat;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * A file handler that writes log events to a file.
 * <p>
 * This handler use a {@link BufferedWriter} to write {@link LogEvent}s to a file. You can configure the following
 * properties:
 * <ul>
 *     <li>{@code file} - the {@link Path} of the file</li>
 *     <li>{@code append} - whether to append to the file or not</li>
 * </ul>
 */
public class FileHandler extends AbstractSyncedHandler {

    private static final String FILE_NAME_PROPERTY = "%s.file";
    private static final String APPEND_PROPERTY = "%s.append";
    private static final String DEFAULT_FILE_NAME = "swirlds-log.log";
    private final OverBufferedWriter appendable;


    /**
     * Creates a new file handler.
     *
     * @param configKey     the configuration key
     * @param configuration the configuration
     */
    public FileHandler(@NonNull final String configKey, @NonNull final Configuration configuration) {
        super(configKey, configuration);

        final String propertyPrefix = PROPERTY_HANDLER.formatted(configKey);
        final Path filePath = Objects.requireNonNullElse(
                configuration.getValue(FILE_NAME_PROPERTY.formatted(propertyPrefix), Path.class, null),
                Path.of(DEFAULT_FILE_NAME));
        final boolean append = Objects.requireNonNullElse(
                configuration.getValue(APPEND_PROPERTY.formatted(propertyPrefix), Boolean.class, null), true);

        BufferedWriter bufferedWriter = null;
        try {
            if (!Files.exists(filePath) || Files.isWritable(filePath)) {
                if (append) {
                    bufferedWriter = Files.newBufferedWriter(
                            filePath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.DSYNC);
                } else {
                    bufferedWriter = Files.newBufferedWriter(
                            filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
                }
            } else {
                EMERGENCY_LOGGER.log(Level.ERROR, "Log file could not be created or written to");
            }
        } catch (final Exception exception) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Failed to create FileHandler", exception);
        }
        this.appendable = new OverBufferedWriter(bufferedWriter, getName());
    }

    /**
     * Handles a log event by appending it to the file using the {@link LineBasedFormat}.
     *
     * @param event The log event to be printed.
     */
    @Override
    protected void handleEvent(@NonNull final LogEvent event) {

        if (appendable != null) {
            final StringBuilder writer = new StringBuilder(4 * 1024);
            LineBasedFormat.print(writer, event);
            appendable.write(writer.toString().toCharArray(), writer.length());
        }

    }

    protected void handleStopAndFinalize() {
        try {
            if (appendable != null) {
                appendable.flush();
                appendable.close();
            }
        } catch (final Exception exception) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Failed to close file output stream", exception);
        }
    }

    private static class OverBufferedWriter implements Closeable {

        private static final int BUFFER_SIZE = 8192 * 4;
        private final CharBuffer buffer;
        private final BufferedWriter bufferedWriter;

        private final String name;

        public OverBufferedWriter(@Nullable final BufferedWriter bufferedWriter, @NonNull final String name) {
            this.buffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(new byte[BUFFER_SIZE]));
            this.bufferedWriter = bufferedWriter;
            this.name = name;
        }

        private String getName() {
            return name;
        }

        public synchronized void write(
                final char[] bytes, final int length) {

            if (length >= buffer.capacity()) {
                // if request length exceeds buffer capacity, flush the buffer and write the data directly
                flush();
                writeToDestination(bytes, 0, length);
            } else {
                if (length > buffer.remaining()) {
                    flush();
                }
                buffer.put(bytes, 0, length);
            }
        }

        /**
         * Writes the specified section of the specified byte array to the stream.
         *
         * @param bytes  the array containing data
         * @param offset from where to write
         * @param length how many bytes to write
         */
        private void writeToDestination(final char[] bytes, final int offset, final int length) {
            if (bufferedWriter != null) {
                try {
                    bufferedWriter.write(bytes, offset, length);
                } catch (final IOException ex) {
                    throw new RuntimeException("Error writing to stream " + getName(), ex);
                }
            }
        }

        /**
         * Calls {@code flush()} on the underlying output stream.
         */
        public synchronized void flush() {
            flushBuffer(buffer);
            flushDestination();
        }

        private void flushDestination() {
            if (bufferedWriter != null) {
                try {
                    bufferedWriter.flush();
                } catch (final IOException ex) {
                    throw new RuntimeException("Error flushing stream " + getName(), ex);
                }
            }
        }


        private void flushBuffer(final CharBuffer buf) {
            ((Buffer) buf).flip();
            try {
                if (buf.remaining() > 0) {
                    writeToDestination(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
                }
            } finally {
                buf.clear();
            }
        }

        /**
         * Closes and releases any system resources associated with this instance.
         */
        @Override
        public void close() {

        }
    }
}
