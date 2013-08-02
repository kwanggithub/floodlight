/**
*    Copyright 2011, Big Switch Networks, Inc.
*    Originally created by David Erickson, Stanford University
*
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package net.floodlightcontroller.core;

import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.floodlightcontroller.core.types.MacVlanPair;

import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Return switch statistics information for all switches
 *
 */
public class AllSwitchStatistics {
    protected static final Logger log =
        LoggerFactory.getLogger(AllSwitchStatistics.class);
    public enum REQUESTTYPE {
        OFSTATS,
        OFFEATURES
    }
    public static Map<String, Object> retrieveInternal(
                               String statType,
                               IFloodlightProviderService floodlightProvider) {
        Map<String, Object> model = new TreeMap<String, Object>();

        OFStatisticsType type = null;
        REQUESTTYPE rType = null;

        if (statType.equals("port")) {
            type = OFStatisticsType.PORT;
            rType = REQUESTTYPE.OFSTATS;
        } else if (statType.equals("queue")) {
            type = OFStatisticsType.QUEUE;
            rType = REQUESTTYPE.OFSTATS;
        } else if (statType.equals("flow")) {
            type = OFStatisticsType.FLOW;
            rType = REQUESTTYPE.OFSTATS;
        } else if (statType.equals("aggregate")) {
            type = OFStatisticsType.AGGREGATE;
            rType = REQUESTTYPE.OFSTATS;
        } else if (statType.equals("desc")) {
            type = OFStatisticsType.DESC;
            rType = REQUESTTYPE.OFSTATS;
        } else if (statType.equals("table")) {
            type = OFStatisticsType.TABLE;
            rType = REQUESTTYPE.OFSTATS;
        } else if (statType.equals("features")) {
            rType = REQUESTTYPE.OFFEATURES;
        } else {
            return model;
        }

        Long[] switchDpids = floodlightProvider.getAllSwitchDpids().toArray(new Long[0]);
        List<GetConcurrentStatsThread> activeThreads = new ArrayList<GetConcurrentStatsThread>(switchDpids.length);
        List<GetConcurrentStatsThread> pendingRemovalThreads = new ArrayList<GetConcurrentStatsThread>();
        GetConcurrentStatsThread t;
        for (Long l : switchDpids) {
            t = new GetConcurrentStatsThread(l, rType, type, floodlightProvider);
            activeThreads.add(t);
            t.start();
        }

        // Join all the threads after the timeout. Set a hard timeout
        // of 12 seconds for the threads to finish. If the thread has not
        // finished the switch has not replied yet and therefore we won't
        // add the switch's stats to the reply.
        for (int iSleepCycles = 0; iSleepCycles < 12; iSleepCycles++) {
            for (GetConcurrentStatsThread curThread : activeThreads) {
                if (curThread.getState() == State.TERMINATED) {
                    if (rType == REQUESTTYPE.OFSTATS) {
                        model.put(HexString.toHexString(curThread.getSwitchId()), curThread.getStatisticsReply());
                    } else if (rType == REQUESTTYPE.OFFEATURES) {
                        model.put(HexString.toHexString(curThread.getSwitchId()), curThread.getFeaturesReply());
                    }
                    pendingRemovalThreads.add(curThread);
                }
            }

            // remove the threads that have completed the queries to the switches
            for (GetConcurrentStatsThread curThread : pendingRemovalThreads) {
                activeThreads.remove(curThread);
            }
            // clear the list so we don't try to double remove them
            pendingRemovalThreads.clear();

            // if we are done finish early so we don't always get the worst case
            if (activeThreads.isEmpty()) {
                break;
            }

            // sleep for 1 s here
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for statistics", e);
            }
        }

        return model;
    }

    protected static class GetConcurrentStatsThread extends Thread {
        private List<OFStatistics> switchReply;
        private final long switchId;
        private final OFStatisticsType statType;
        private final REQUESTTYPE requestType;
        private final OFFeaturesReply featuresReply;
        private final Map<MacVlanPair, Short> switchTable;
        private final IFloodlightProviderService floodlightProvider;

        public GetConcurrentStatsThread(long switchId, REQUESTTYPE requestType,
                                        OFStatisticsType statType,
                                        IFloodlightProviderService floodlightProvider) {
            this.switchId = switchId;
            this.requestType = requestType;
            this.statType = statType;
            this.switchReply = null;
            this.featuresReply = null;
            this.switchTable = null;
            this.floodlightProvider = floodlightProvider;
        }

        public List<OFStatistics> getStatisticsReply() {
            return switchReply;
        }

        public OFFeaturesReply getFeaturesReply() {
            return featuresReply;
        }

        public Map<MacVlanPair, Short> getSwitchTable() {
            return switchTable;
        }

        public long getSwitchId() {
            return switchId;
        }

        @Override
        public void run() {
            if ((requestType == REQUESTTYPE.OFSTATS) && (statType != null)) {
                switchReply = SwitchStatistics.getSwitchStatistics(switchId, statType,
                                                                   floodlightProvider, log);
            }
        }
    }
}
