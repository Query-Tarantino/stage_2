package org.ulpgc.tarantino.common.hazelcast;

import com.hazelcast.core.LifecycleEvent;
import com.hazelcast.core.LifecycleListener;

import java.util.concurrent.atomic.AtomicBoolean;

final class ConnectionState implements LifecycleListener {

    private final AtomicBoolean connected = new AtomicBoolean();

    @Override
    public void stateChanged(LifecycleEvent event) {
        switch (event.getState()) {
            case CLIENT_CONNECTED -> connected.set(true);
            case CLIENT_DISCONNECTED, SHUTTING_DOWN, SHUTDOWN -> connected.set(false);
            default -> {
            }
        }
    }

    boolean connected() {
        return connected.get();
    }
}
