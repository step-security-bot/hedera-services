package com.swirlds.platform.gossip.sync;

import com.swirlds.common.system.NodeId;

import com.swirlds.logging.legacy.LogMarker;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LocalReconnect {
    private static final Logger logger = LogManager.getLogger(LocalReconnect.class);
    private static final List<NodePause> nodesToPause = List.of(
            new NodePause(3, 20, 20),
            new NodePause(3, 50, 20),
            new NodePause(0, 80, 20),
            new NodePause(1, 110, 20),
            new NodePause(2, 140, 20)
    );
    private static final Instant startTime = Instant.now();

    public static boolean shouldPause(final NodeId nodeId) {
        return nodesToPause
                .stream()
                .filter(nodePause -> nodePause.nodeId.equals(nodeId))
                .anyMatch(NodePause::shouldPause);
    }

    private record NodePause(NodeId nodeId, Duration pauseStart, Duration pauseDuration, AtomicBoolean shunned) {
        public NodePause(final long nodeId, final int pauseStart, final int pauseDuration) {
            this(
                    new NodeId(nodeId),
                    Duration.ofSeconds(pauseStart),
                    Duration.ofSeconds(pauseDuration),
                    new AtomicBoolean(false)
            );
        }

        public boolean shouldPause() {
            final Instant now = Instant.now();
            if (now.isBefore(startTime.plus(pauseStart))) {
                if (shunned.compareAndSet(true, false)) {
                    logger.info(LogMarker.STARTUP.getMarker(), "Stopped shunning node {} at {}", nodeId, pauseStart);
                }
                return false;
            }
            if (now.isBefore(startTime.plus(pauseStart).plus(pauseDuration))) {
                if (shunned.compareAndSet(false, true)) {
                    logger.info(LogMarker.STARTUP.getMarker(), "Shunning node {} at {}", nodeId, pauseStart);
                }
                return true;
            }
            if (shunned.compareAndSet(true, false)) {
                logger.info(LogMarker.STARTUP.getMarker(), "Stopped shunning node {} at {}", nodeId, pauseStart);
            }
            return false;
        }
    }
}
