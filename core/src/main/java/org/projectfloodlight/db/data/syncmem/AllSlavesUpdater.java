package org.projectfloodlight.db.data.syncmem;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Iterables;

class AllSlavesUpdater {
    private final static Logger logger = LoggerFactory.getLogger(AllSlavesUpdater.class);

    private final ConcurrentMap<SlaveId, SlaveUpdater> slaveUpdaters;

    enum State {
        CREATED, RUNNING, STOPPED, PAUSED
    };

    private volatile State state = State.CREATED;
    private final HttpClientFactory clientFactory;

    private volatile SyncContent pendingContent;

    public AllSlavesUpdater(HttpClientFactory factory) {
        this.clientFactory = factory;
        this.slaveUpdaters = new ConcurrentHashMap<SlaveId, SlaveUpdater>();
        this.pendingContent = null;
    }

    public SyncContent getPendingContent() {
        return pendingContent;
    }


    public Map<SlaveId, SyncContent> getSlaveCurrentContents() {
        Builder<SlaveId, SyncContent> builder = ImmutableMap.<SlaveId, SyncContent>builder();
        for (Map.Entry<SlaveId, SlaveUpdater> entry : slaveUpdaters.entrySet()) {
            SlaveId id = entry.getKey();
            SlaveUpdater updater = entry.getValue();
            builder.put(id, updater.getCurrentContent());
        }
        return builder.build();
    }

    public synchronized void setContent(SyncContent content) {
        if(logger.isDebugEnabled())
            logger.debug("AllSlavesUpdater: set content to "+content);

        this.pendingContent = content;
        notifyAll();
    }

    public Iterable<SlaveId> getSlaveIds() {
        return Iterables.unmodifiableIterable(slaveUpdaters.keySet());
    }

    public void retainSlaves(Collection<SlaveId> retainSlaves) {
        logger.debug("retainSlaves: "+retainSlaves);
        slaveUpdaters.keySet().retainAll(retainSlaves);
    }

    public boolean hasSlave(SlaveId id) {
        return slaveUpdaters.containsKey(id);
    }

    public synchronized void addSlave(SlaveId id, URI uri) {
        SlaveUpdater updater = new SlaveUpdater(id, clientFactory.getClient(uri));
        slaveUpdaters.put(id, updater);
        notifyAll();
    }

    public synchronized void removeSlave(SlaveId id) {
        slaveUpdaters.remove(id);
        notifyAll();
    }

    public synchronized void start() {
        if (state != State.CREATED)
            throw new IllegalStateException("not in state CREATED but "+state);
        this.state = State.RUNNING;
        Thread updateThread = new Thread(new SlaveUpdateRunner(), "SlaveUpdateRunner");
        updateThread.setDaemon(true);
        updateThread.start();
    }

    public synchronized void shutdown() {
        if (state == State.RUNNING) {
            state = State.STOPPED;
            notifyAll();
        }
        clientFactory.closeAll();
    }

    public synchronized void pause() {
        if (state ==State.RUNNING) {
            state = State.PAUSED;
            notifyAll();
        }
    }

    public synchronized void resume() {
        if(state == State.PAUSED) {
            state = State.RUNNING;
            notifyAll();
        }
    }


    public State getState() {
        return state;
    }

    class SlaveUpdateRunner implements Runnable {
        private final long MAX_WAIT_TIME = TimeUnit.SECONDS.toNanos(2);

        @Override
        public void run() {
            while (state != State.STOPPED) {

                if (state == State.RUNNING) {
                    if(logger.isTraceEnabled())
                        logger.trace("running - checking for slaves that need sync");

                    // update pending updaters out of sync
                    for (Map.Entry<SlaveId, SlaveUpdater> entry : slaveUpdaters.entrySet()) {
                        SlaveUpdater updater = entry.getValue();
                        updater.setContent(pendingContent);
                        if (updater.needsUpdate()) {
                            if(logger.isTraceEnabled())
                                logger.trace("slave "+updater + " needs sync");
                            updater.update();
                        }
                    }

                    // check for updaters needing sync now, wait in synchronized block so we don't miss one
                    synchronized (AllSlavesUpdater.this) {
                        long waitTime = MAX_WAIT_TIME;
                        for (Map.Entry<SlaveId, SlaveUpdater> entry : slaveUpdaters
                                .entrySet()) {
                            SlaveUpdater updater = entry.getValue();
                            updater.setContent(pendingContent);
                            if (updater.needsUpdate()) {
                                long updWaitTime = updater.getWaitTime();
                                if (waitTime > updWaitTime)
                                    waitTime = updWaitTime;
                            }

                        }
                        if (waitTime > 10) {
                            try {
                                if(logger.isTraceEnabled())
                                    logger.trace("waiting for "+waitTime + " ns");
                                long waitMs = TimeUnit.NANOSECONDS.toMillis(waitTime);
                                int waitNs = (int) (waitTime - TimeUnit.MILLISECONDS.toNanos(waitMs));
                                AllSlavesUpdater.this.wait(waitMs, waitNs);
                            } catch (InterruptedException e) {
                                logger.debug("thread interrupted while waiting for timeout", e);
                            }
                        }
                    }
                } else {
                    synchronized (AllSlavesUpdater.this) {
                        // double checked locking - only enter wait if we're really in PAUSED state
                        if (state == State.PAUSED) {
                            try {
                                AllSlavesUpdater.this.wait(MAX_WAIT_TIME);
                            } catch (InterruptedException e) {
                                logger.debug("thread interrupted while waiting for pause to end", e);
                            }
                        }
                    }
                }
            }
        }
    }

}
