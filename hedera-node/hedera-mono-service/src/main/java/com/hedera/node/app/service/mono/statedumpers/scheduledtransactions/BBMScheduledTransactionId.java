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

package com.hedera.node.app.service.mono.statedumpers.scheduledtransactions;

import com.google.common.collect.ComparisonChain;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKey;
import edu.umd.cs.findbugs.annotations.NonNull;

public record BBMScheduledTransactionId(long num) implements Comparable<BBMScheduledTransactionId> {
    static BBMScheduledTransactionId fromMono(@NonNull final EntityNumVirtualKey key) {
        return new BBMScheduledTransactionId(key.getKeyAsLong());
    }

    static BBMScheduledTransactionId fromMono(@NonNull final SecondSinceEpocVirtualKey key) {
        return new BBMScheduledTransactionId(key.getKeyAsLong());
    }

    static BBMScheduledTransactionId fromMono(@NonNull final ScheduleEqualityVirtualKey key) {
        return new BBMScheduledTransactionId(key.getKeyAsLong());
    }

    @Override
    public String toString() {
        return "%d".formatted(num);
    }

    @Override
    public int compareTo(BBMScheduledTransactionId o) {
        return ComparisonChain.start().compare(this.num, o.num).result();
    }
}
