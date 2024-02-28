/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.logging.api.internal.format;

import static java.time.ZoneOffset.UTC;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

public class EpocMillisParser {

    /**
     * The formatter for the timestamp.
     */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());
    private final Map<Instant, String> exactCache = new LimitedSizeCache<>();
    private final Map<Instant, String> dateCache = new LimitedSizeCache<>();
    private final Map<Instant, String> dateTimeHourCache = new LimitedSizeCache<>();
    private final Map<Instant, String> dateTimeHourMinutesCache = new LimitedSizeCache<>();

    public String parse(long value) {
        Instant instant = Instant.ofEpochMilli(value);

        String s;
        if ((s = exactCache.get(instant)) != null) {
            return s;
        }
        if ((s = getFromMinutes(instant)) != null) {
            exactCache.put(instant, s);
            return s;
        }
        if ((s = getFromHours(instant)) != null) {
            exactCache.put(instant, s);
            return s;
        }
        if ((s = getFromDate(instant)) != null) {
            exactCache.put(instant, s);
            return s;
        }

        s = FORMATTER.withZone(UTC).format(instant);
        exactCache.put(instant, s);
        dateCache.put(instant.truncatedTo(ChronoUnit.DAYS), s.substring(0, 10));
        dateTimeHourCache.put(instant.truncatedTo(ChronoUnit.HOURS), s.substring(0, 13));
        dateTimeHourMinutesCache.put(instant.truncatedTo(ChronoUnit.MINUTES), s.substring(0, 17));
        return s;
    }

    private String getFromMinutes(final Instant instant) {
        final String format = dateTimeHourMinutesCache.get(instant.truncatedTo(ChronoUnit.MINUTES));
        if (format == null) {
            return null;
        }
        return format + stringFrom(ChronoUnit.SECONDS, instant);
    }

    private String getFromHours(final Instant instant) {
        final String format = dateTimeHourCache.get(instant.truncatedTo(ChronoUnit.HOURS));
        if (format == null) {
            return null;
        }
        return format + stringFrom(ChronoUnit.MINUTES, instant);
    }

    private String getFromDate(final Instant instant) {
        final String format = dateCache.get(instant.truncatedTo(ChronoUnit.DAYS));

        if (format == null) {
            return null;
        }

        return format + stringFrom(ChronoUnit.HOURS, instant);
    }

    private StringBuilder stringFrom(final ChronoUnit unit, final Instant instant) {

        // Get the total seconds and milliseconds
        long totalSeconds = instant.getEpochSecond();

        final StringBuilder stringBuilder = new StringBuilder();
        if (unit.ordinal() >= ChronoUnit.MILLIS.ordinal()) {
            int milliseconds = instant.getNano() / 1_000_000; // Convert nanoseconds to milliseconds
            stringBuilder.append(milliseconds);
            if (milliseconds <= 9) {
                stringBuilder.append(0);
            }
            if (milliseconds <= 99) {
                stringBuilder.append(0);
            }
        }
        if (unit.ordinal() >= ChronoUnit.SECONDS.ordinal()) {
            stringBuilder.append(".");
            int second = (int) (totalSeconds % 60);
            stringBuilder.append(second);
            if (second <= 9) {
                stringBuilder.append(0);
            }

        }
        if (unit.ordinal() >= ChronoUnit.MINUTES.ordinal()) {
            int minute = (int) ((totalSeconds / 60) % 60);
            stringBuilder.append(":");
            stringBuilder.append(minute);
            if (minute <= 9) {
                stringBuilder.append(0);
            }

        }
        if (unit.ordinal() >= ChronoUnit.HOURS.ordinal()) {
            int hour = (int) ((totalSeconds / 3600) % 24);
            stringBuilder.append(":");
            stringBuilder.append(hour);
            if (hour <= 9) {
                stringBuilder.append(0);
            }
            stringBuilder.append("T");
        }

        return stringBuilder.reverse();
    }

    private static class LimitedSizeCache<K, V> extends LinkedHashMap<K, V> {
        private static final int MAX_ENTRIES = 20000;

        public LimitedSizeCache() {
            super(MAX_ENTRIES + 1, 1.0f, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > MAX_ENTRIES;
        }
    }
}
