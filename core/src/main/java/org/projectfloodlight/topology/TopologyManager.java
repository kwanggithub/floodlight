/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

package org.projectfloodlight.topology;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.projectfloodlight.core.FloodlightContext;
import org.projectfloodlight.core.HAListenerTypeMarker;
import org.projectfloodlight.core.IFloodlightProviderService;
import org.projectfloodlight.core.IHAListener;
import org.projectfloodlight.core.IInfoProvider;
import org.projectfloodlight.core.IOFMessageListener;
import org.projectfloodlight.core.IOFSwitch;
import org.projectfloodlight.core.IFloodlightProviderService.Role;
import org.projectfloodlight.core.annotations.LogMessageCategory;
import org.projectfloodlight.core.annotations.LogMessageDoc;
import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.core.module.FloodlightModuleException;
import org.projectfloodlight.core.module.IFloodlightModule;
import org.projectfloodlight.core.module.IFloodlightService;
import org.projectfloodlight.core.util.SingletonTask;
import org.projectfloodlight.counter.ICounterStoreService;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.IBigDBService;
import org.projectfloodlight.db.data.ServerDataSource;
import org.projectfloodlight.db.util.Path;
import org.projectfloodlight.debugcounter.IDebugCounter;
import org.projectfloodlight.debugcounter.IDebugCounterService;
import org.projectfloodlight.debugcounter.NullDebugCounter;
import org.projectfloodlight.debugcounter.IDebugCounterService.CounterException;
import org.projectfloodlight.debugcounter.IDebugCounterService.CounterType;
import org.projectfloodlight.debugevent.IDebugEventService;
import org.projectfloodlight.debugevent.IEventUpdater;
import org.projectfloodlight.debugevent.NullDebugEvent;
import org.projectfloodlight.debugevent.IDebugEventService.EventColumn;
import org.projectfloodlight.debugevent.IDebugEventService.EventFieldType;
import org.projectfloodlight.debugevent.IDebugEventService.EventType;
import org.projectfloodlight.debugevent.IDebugEventService.MaxEventsRegistered;
import org.projectfloodlight.linkdiscovery.ILinkDiscoveryListener;
import org.projectfloodlight.linkdiscovery.ILinkDiscoveryService;
import org.projectfloodlight.packet.BSN;
import org.projectfloodlight.packet.Ethernet;
import org.projectfloodlight.packet.LLDP;
import org.projectfloodlight.routing.IRoutingService;
import org.projectfloodlight.routing.Link;
import org.projectfloodlight.routing.Route;
import org.projectfloodlight.threadpool.IThreadPoolService;
import org.projectfloodlight.topology.TunnelEvent.TunnelLinkStatus;
import org.projectfloodlight.topology.bigdb.TopologyResource;
import org.projectfloodlight.tunnel.ITunnelManagerListener;
import org.projectfloodlight.tunnel.ITunnelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bigswitch.floodlight.vendor.OFActionTunnelDstIP;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Topology manager is responsible for maintaining the controller's notion
 * of the network graph, as well as implementing tools for finding routes
 * through the topology.
 */
@LogMessageCategory("Network Topology")
public class TopologyManager implements
        IFloodlightModule, ITopologyService,
        IRoutingService, ILinkDiscoveryListener,
        IOFMessageListener, IInfoProvider, ITunnelManagerListener {

    protected final static Logger log = 
            LoggerFactory.getLogger(TopologyManager.class);

    public static final String MODULE_NAME = "topology";

    public static final String CONTEXT_TUNNEL_ENABLED =
            "com.bigswitch.floodlight.topologymanager.tunnelEnabled";

    /**
     * Role of the controller.
     */
    private Role role;

    /**
     * Set of ports for each switch
     */
    protected Map<Long, Set<Short>> switchPorts;

    /**
     * Set of links organized by node port tuple
     */
    protected Map<NodePortTuple, Set<Link>> switchPortLinks;

    /**
     * Set of direct links
     */
    protected Map<NodePortTuple, Set<Link>> directLinks;

    /**
     * set of links that are broadcast domain links.
     */
    protected Map<NodePortTuple, Set<Link>> portBroadcastDomainLinks;

    /**
     * set of tunnel links
     */
    protected Set<NodePortTuple> tunnelPorts;

    protected ILinkDiscoveryService linkDiscovery;
    protected IThreadPoolService threadPool;
    protected IFloodlightProviderService floodlightProvider;
    protected IBigDBService bigDB;
    protected IDebugCounterService debugCounters;
    protected ITunnelService tunnelManager = null;

    // Modules that listen to our updates
    protected ArrayList<ITopologyListener> topologyAware;

    protected BlockingQueue<LDUpdate> ldUpdates;

    // These must be accessed using getCurrentInstance(), not directly
    protected TopologyInstance currentInstance;
    protected TopologyInstance currentInstanceWithoutTunnels;

    protected SingletonTask newInstanceTask;
    private Date lastUpdateTime;
    private boolean shouldRunNewInstanceTask = true;

    /**
     * Flag that indicates if links (direct/tunnel/multihop links) were
     * updated as part of LDUpdate.
     */
    protected boolean linksUpdated;
    /**
     * Flag that indicates if direct or tunnel links were updated as
     * part of LDUpdate.
     */
    protected boolean dtLinksUpdated;

    /** Flag that indicates if tunnel ports were updated or not
     */
    protected boolean tunnelPortsUpdated;

    protected int TOPOLOGY_COMPUTE_INTERVAL_MS = 500;

    Set<BroadcastDomain> broadcastDomains;
    Long tunnelDomain;

    // Multipath
    boolean multipathEnabled;

    // NOF Traffic Spreading
    // This flag enables if the traffic is spread across the links
    // connecting to a broadcast domain or not.
    boolean nofTrafficSpreading;

    // Declarations for tunnel liveness detection
    // The ordered node pairs here are switch DPIDs.
    /**
     * Queue to detect tunnel failures.  The queue consists of ordered nodepair
     * (srcDPID, dstDPID) that's created whenever we see a flow-mod that is
     * pushed to a switch where the flow starts from the tunnel loopback port.
     * When a flow-mod is pushed to a switch that ends at a tunnel loopback
     * port, we remove the nodepair.  If we do not receive the corresponding
     * flow-mod that ends at tunnel loopback, it is an indication of a potential
     * tunnel failure.  To validate this, we send LLDP on the tunnel ports,
     * and add the tunnel endpoints to the verification queue.  If the LLDP is
     * recieved within a certain time interval, then the tunnel assumed to be
     * alive.  Otherwise, then the tunnel is assumed to have failed.  All tunnel
     * failures are logged as warning messages. The status queue is cleaned
     * every 60 seconds and may be accessed through the CLI.
     */
    BlockingQueue<OrderedNodePair> tunnelDetectionQueue;
    int TUNNEL_DETECTION_TIMEOUT_MS = 2000;  // 2 seconds
    int tunnelDetectionTime = TUNNEL_DETECTION_TIMEOUT_MS;

    /**
     * This queue holds tunnel events for which an LLDP has been sent
     * and waiting for the LLDP to arrive at the other end of the tunnel.
     */
    BlockingQueue<OrderedNodePair> tunnelVerificationQueue;
    int TUNNEL_VERIFICATION_TIMEOUT_MS = 2000;  // 2 seconds
    int tunnelVerificationTime = TUNNEL_VERIFICATION_TIMEOUT_MS;

    /**
     * This queue holds the tunnel events for which LLDP was sent, but
     * the LLDP was not received within the desired time.
     */
    ConcurrentHashMap<Long, ConcurrentHashMap<Long, TunnelEvent>> tunnelStatusMap;

    private IHAListener haListener;

    /**
     *  Debug Counters
     */
    protected static final String PACKAGE = TopologyManager.class.getPackage().getName();
    protected IDebugCounter ctrIncoming;

    /**
     * Debug Events
     */
    protected IDebugEventService debugEvents;

    /*
     * Topology Event Updater
     */
    protected IEventUpdater<TopologyEvent> evTopology;

    /**
     * Topology Information exposed for a Topology related event - used inside
     * the BigTopologyEvent class
     */
    protected static class TopologyEventInfo {
        private final int numOpenflowClustersWithTunnels;
        private final int numOpenflowClustersWithoutTunnels;
        private final Map<Long, List<NodePortTuple>> externalPortsMap;
        private final int numTunnelPorts;
        public TopologyEventInfo(int numOpenflowClustersWithTunnels,
                                 int numOpenflowClustersWithoutTunnels,
                                 Map<Long, List<NodePortTuple>> externalPortsMap,
                                 int numTunnelPorts) {
            super();
            this.numOpenflowClustersWithTunnels = numOpenflowClustersWithTunnels;
            this.numOpenflowClustersWithoutTunnels = numOpenflowClustersWithoutTunnels;
            this.externalPortsMap = externalPortsMap;
            this.numTunnelPorts = numTunnelPorts;
        }
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("# Openflow Clusters:");
            builder.append(" { With Tunnels: ");
            builder.append(numOpenflowClustersWithTunnels);
            builder.append(" Without Tunnels: ");
            builder.append(numOpenflowClustersWithoutTunnels);
            builder.append(" }");
            builder.append(", # External Clusters: ");
            int numExternalClusters = externalPortsMap.size();
            builder.append(numExternalClusters);
            if (numExternalClusters > 0) {
                builder.append(" { ");
                int count = 0;
                for (Long extCluster : externalPortsMap.keySet()) {
                    builder.append("#" + extCluster + ":Ext Ports: ");
                    builder.append(externalPortsMap.get(extCluster).size());
                    if (++count < numExternalClusters) {
                        builder.append(", ");
                    } else {
                        builder.append(" ");
                    }
                }
                builder.append("}");
            }
            builder.append(", # Tunnel Ports: ");
            builder.append(numTunnelPorts);
            return builder.toString();
        }
    }

    /**
     * Topology Event class to track topology related events
     */
    protected class TopologyEvent {
        @EventColumn(name = "Reason", description = EventFieldType.STRING)
        private final String reason;
        @EventColumn(name = "Topology Summary")
        private final TopologyEventInfo topologyInfo;
        public TopologyEvent(String reason,
                TopologyEventInfo topologyInfo) {
            super();
            this.reason = reason;
            this.topologyInfo = topologyInfo;
        }
    }

   //  Getter/Setter methods
    /**
     * Get the time interval for the period topology updates, if any.
     * The time returned is in milliseconds.
     * @return
     */
    public int getTopologyComputeInterval() {
        return TOPOLOGY_COMPUTE_INTERVAL_MS;
    }

    /**
     * Set the time interval for the period topology updates, if any.
     * The time is in milliseconds.
     * @return
     */
    public void setTopologyComputeInterval(int time_ms) {
        TOPOLOGY_COMPUTE_INTERVAL_MS = time_ms;
    }

    /**
     * An empty constructor as required by the module loader.
     */
    public TopologyManager() {}

    /**
     * This constructor is used for unit testing.
     * @param shouldRunTask Whether to run the background thread
     * that sends out LLDPs, etc.
     */
    public TopologyManager(boolean shouldRunTask) {
        this.shouldRunNewInstanceTask = shouldRunTask;
    }

    /**
     * Thread for recomputing topology.  The thread is always running,
     * however the function applyUpdates() has a blocking call.
     */
    @LogMessageDoc(level="ERROR",
            message="Error in topology instance task thread",
            explanation="An unknown error occured in the topology " +
                    "discovery module.",
            recommendation=LogMessageDoc.CHECK_CONTROLLER)
    protected class UpdateTopologyWorker implements Runnable {
        @Override
        public void run() {
            if (!shouldRunNewInstanceTask) return;
            try {
                if (ldUpdates.peek() != null)
                    updateTopology();
                handleMiscellaneousPeriodicEvents();
            }
            catch (Exception e) {
                log.error("Error in topology instance task thread", e);
            } finally {
                if (floodlightProvider.getRole() != Role.SLAVE)
                    newInstanceTask.reschedule(TOPOLOGY_COMPUTE_INTERVAL_MS,
                                           TimeUnit.MILLISECONDS);
            }
        }
    }

    public boolean updateTopology() {
        boolean newInstanceFlag;
        linksUpdated = false;
        dtLinksUpdated = false;
        tunnelPortsUpdated = false;
        List<LDUpdate> appliedUpdates = applyUpdates();
        newInstanceFlag = createNewInstance("link-discovery-updates");
        lastUpdateTime = new Date();
        informListeners(appliedUpdates);
        return newInstanceFlag;
    }

    // **********************
    // ILinkDiscoveryListener
    // **********************
    @Override
    public void linkDiscoveryUpdate(List<LDUpdate> updateList) {
        if (log.isTraceEnabled()) {
            log.trace("Queuing update: {}", updateList);
        }
        ldUpdates.addAll(updateList);
    }

    @Override
    public void linkDiscoveryUpdate(LDUpdate update) {
        if (log.isTraceEnabled()) {
            log.trace("Queuing update: {}", update);
        }
        ldUpdates.add(update);
    }

    // ****************
    // ITopologyService
    // ****************

    //
    // ITopologyService interface methods
    //
    @Override
    @SuppressFBWarnings(value="EI_EXPOSE_REP")
    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    @Override
    public void addListener(ITopologyListener listener) {
        topologyAware.add(listener);
    }

    @Override
    public boolean isAttachmentPointPort(long switchid, short port) {
        return isAttachmentPointPort(switchid, port, true);
    }

    @Override
    public boolean isAttachmentPointPort(long switchid, short port,
                                         boolean tunnelEnabled) {

        // If the switch port is 'tun-bsn' port, it is not
        // an attachment point port, irrespective of whether
        // a link is found through it or not.
        if (linkDiscovery.isTunnelPort(switchid, port))
            return false;

        TopologyInstance ti = getCurrentInstance(tunnelEnabled);

        // if the port is not attachment point port according to
        // topology instance, then return false
        if (ti.isAttachmentPointPort(switchid, port) == false)
                return false;

        // Check whether the port is a physical port. We should not learn
        // attachment points on "special" ports.
        if ((port & 0xff00) == 0xff00 && port != (short)0xfffe) return false;

        // Make sure that the port is enabled.
        IOFSwitch sw = floodlightProvider.getSwitch(switchid);
        if (sw == null) return false;
        return (sw.portEnabled(port));
    }

    @Override
    public long getOpenflowDomainId(long switchId) {
        return getOpenflowDomainId(switchId, true);
    }

    @Override
    public long getOpenflowDomainId(long switchId, boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getOpenflowDomainId(switchId);
    }

    @Override
    public long getL2DomainId(long switchId) {
        return getL2DomainId(switchId, true);
    }

    @Override
    public long getL2DomainId(long switchId, boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getL2DomainId(switchId);
    }

    @Override
    public boolean inSameOpenflowDomain(long switch1, long switch2) {
        return inSameOpenflowDomain(switch1, switch2, true);
    }

    @Override
    public boolean inSameOpenflowDomain(long switch1, long switch2,
                                        boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.inSameOpenflowDomain(switch1, switch2);
    }

    @Override
    public boolean isAllowed(long sw, short portId) {
        return isAllowed(sw, portId, true);
    }

    @Override
    public boolean isAllowed(long sw, short portId, boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.isAllowed(sw, portId);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    @Override
    public boolean isIncomingBroadcastAllowed(long sw, short portId) {
        return isIncomingBroadcastAllowed(sw, portId, true);
    }

    @Override
    public boolean isIncomingBroadcastAllowed(long sw, short portId,
                                              boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.isIncomingBroadcastAllowedOnSwitchPort(sw, portId);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    /** Get all the ports connected to the switch */
    @Override
    public Set<Short> getPortsWithLinks(long sw) {
        return getPortsWithLinks(sw, true);
    }

    /** Get all the ports connected to the switch */
    @Override
    public Set<Short> getPortsWithLinks(long sw, boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getPortsWithLinks(sw);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    /** Get all the ports on the target switch (targetSw) on which a
     * broadcast packet must be sent from a host whose attachment point
     * is on switch port (src, srcPort).
     */
    @Override
    public Set<Short> getBroadcastPorts(long targetSw,
                                        long src, short srcPort) {
        return getBroadcastPorts(targetSw, src, srcPort, true);
    }

    /** Get all the ports on the target switch (targetSw) on which a
     * broadcast packet must be sent from a host whose attachment point
     * is on switch port (src, srcPort).
     */
    @Override
    public Set<Short> getBroadcastPorts(long targetSw,
                                        long src, short srcPort,
                                        boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getBroadcastPorts(targetSw, src, srcPort);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    @Override
    public NodePortTuple getOutgoingSwitchPort(long src, short srcPort,
                                               long dst, short dstPort) {
        // Use this function to redirect traffic if needed.
        return getOutgoingSwitchPort(src, srcPort, dst, dstPort, true);
    }

    @Override
    public NodePortTuple getOutgoingSwitchPort(long src, short srcPort,
                                               long dst, short dstPort,
                                               boolean tunnelEnabled) {
        // Use this function to redirect traffic if needed.
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getOutgoingSwitchPort(src, srcPort,
                                                     dst, dstPort);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    @Override
    public NodePortTuple getIncomingSwitchPort(long src, short srcPort,
                                               long dst, short dstPort) {
        return getIncomingSwitchPort(src, srcPort, dst, dstPort, true);
    }

    @Override
    public NodePortTuple getIncomingSwitchPort(long src, short srcPort,
                                               long dst, short dstPort,
                                               boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getIncomingSwitchPort(src, srcPort,
                                                     dst, dstPort);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    /**
     * Checks if the two switchports belong to the same broadcast domain.
     */
    @Override
    public boolean isInSameBroadcastDomain(long s1, short p1, long s2,
                                           short p2) {
        return isInSameBroadcastDomain(s1, p1, s2, p2, true);

    }

    @Override
    public boolean isInSameBroadcastDomain(long s1, short p1,
                                           long s2, short p2,
                                           boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.inSameBroadcastDomain(s1, p1, s2, p2);

    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    /**
     * Checks if the switchport is a broadcast domain port or not.
     */
    @Override
    public boolean isBroadcastDomainPort(long sw, short port) {
        return isBroadcastDomainPort(sw, port, true);
    }

    @Override
    public boolean isBroadcastDomainPort(long sw, short port,
                                         boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.isBroadcastDomainPort(new NodePortTuple(sw, port));
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    /**
     * Checks if the new attachment point port is consistent with the
     * old attachment point port.
     */
    @Override
    public boolean isConsistent(long oldSw, short oldPort,
                                long newSw, short newPort) {
        return isConsistent(oldSw, oldPort,
                                            newSw, newPort, true);
    }

    @Override
    public boolean isConsistent(long oldSw, short oldPort,
                                long newSw, short newPort,
                                boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.isConsistent(oldSw, oldPort, newSw, newPort);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    /**
     * Checks if the two switches are in the same Layer 2 domain.
     */
    @Override
    public boolean inSameL2Domain(long switch1, long switch2) {
        return inSameL2Domain(switch1, switch2, true);
    }

    @Override
    public boolean inSameL2Domain(long switch1, long switch2,
                                  boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.inSameL2Domain(switch1, switch2);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    @Override
    public NodePortTuple getAllowedOutgoingBroadcastPort(long src,
                                                         short srcPort,
                                                         long dst,
                                                         short dstPort) {
        return getAllowedOutgoingBroadcastPort(src, srcPort,
                                               dst, dstPort, true);
    }

    @Override
    public NodePortTuple getAllowedOutgoingBroadcastPort(long src,
                                                         short srcPort,
                                                         long dst,
                                                         short dstPort,
                                                         boolean tunnelEnabled){
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getAllowedOutgoingBroadcastPort(src, srcPort,
                                                  dst, dstPort);
    }
    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    @Override
    public NodePortTuple
    getAllowedIncomingBroadcastPort(long src, short srcPort) {
        return getAllowedIncomingBroadcastPort(src,srcPort, true);
    }

    @Override
    public NodePortTuple
    getAllowedIncomingBroadcastPort(long src, short srcPort,
                                    boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getAllowedIncomingBroadcastPort(src,srcPort);
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////
    @Override
    public Set<Long> getSwitchesInOpenflowDomain(long switchDPID) {
        return getSwitchesInOpenflowDomain(switchDPID, true);
    }

    @Override
    public Set<Long> getSwitchesInOpenflowDomain(long switchDPID,
                                                 boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getSwitchesInOpenflowDomain(switchDPID);
    }
    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Set<NodePortTuple> getBroadcastDomainPorts() {
        return portBroadcastDomainLinks.keySet();
    }

    @Override
    public Set<NodePortTuple> getTunnelPorts() {
        return tunnelPorts;
    }

    @Override
    public Set<NodePortTuple> getBlockedPorts() {
        Set<NodePortTuple> bp;
        Set<NodePortTuple> blockedPorts =
                new HashSet<NodePortTuple>();

        // As we might have two topologies, simply get the union of
        // both of them and send it.
        bp = getCurrentInstance(true).getBlockedPorts();
        if (bp != null)
            blockedPorts.addAll(bp);

        bp = getCurrentInstance(false).getBlockedPorts();
        if (bp != null)
            blockedPorts.addAll(bp);

        return blockedPorts;
    }

    @Override
    public Set<BroadcastDomain> getBroadcastDomains() {
        return getCurrentInstance(true).getBroadcastDomains();
    }

    @Override
    public Map<Long, Object> getHigherTopologyNodes() {
        return getCurrentInstance(true).getHTNodes();
    }

    @Override
    public Map<Long, Set<Long>> getHigherTopologyNeighbors() {
        return getCurrentInstance(true).getHTNeighbors();
    }

    @Override
    public Map<Long, Map<Long, Long>> getHigherTopologyNextHops() {
        return getCurrentInstance(true).getHTNextHops();
    }

    @Override
    public Map<Long, Long> getL2DomainIds() {
        return getCurrentInstance(true).getL2DomainIds();
    }

    @Override
    public Map<OrderedNodePair, Set<NodePortTuple>> getAllowedUnicastPorts() {
        return getCurrentInstance(true).getAllowedPorts();
    }

    @Override
    public Map<OrderedNodePair, NodePortTuple> getAllowedIncomingBroadcastPorts() {
        return getCurrentInstance(true).getAllowedIncomingBroadcastPorts();
    }

    @Override
    public Map<NodePortTuple, Set<Long>> getAllowedPortToBroadcastDomains() {
        return getCurrentInstance(true).getAllowedPortsToBroadcastDomains();
    }

    @Override
    public void enableMultipath() {
        multipathEnabled = true;
        createNewInstance("enabled-multipath");
    }

    @Override
    public void disableMultipath() {
        multipathEnabled = false;
        createNewInstance("disabled-multipath");
    }

    public boolean getNOFTrafficSpreading() {
        return nofTrafficSpreading;
    }

    public void setNOFTrafficSpreading(boolean nofTrafficSpreading) {
        this.nofTrafficSpreading = nofTrafficSpreading;
    }
    
    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////

    // ***************
    // IRoutingService
    // ***************

    @Override
    public Route getRoute(long src, long dst, long cookie) {
        return getRoute(src, dst, cookie, true);
    }

    @Override
    public Route getRoute(long src, long dst, long cookie, boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getRoute(src, dst, cookie);
    }

    @Override
    public Route getRoute(long src, short srcPort, long dst, short dstPort, long cookie) {
        return getRoute(src, srcPort, dst, dstPort, cookie, true);
    }

    @Override
    public Route getRoute(long src, short srcPort, long dst, short dstPort, long cookie,
                          boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getRoute(null, src, srcPort, dst, dstPort, cookie);
    }

    @Override
    public boolean routeExists(long src, long dst) {
        return routeExists(src, dst, true);
    }

    @Override
    public boolean routeExists(long src, long dst, boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.routeExists(src, dst);
    }

    @Override
    public ArrayList<Route> getRoutes(long srcDpid, long dstDpid,
                                      boolean tunnelEnabled) {
        TopologyInstance ti = getCurrentInstance(tunnelEnabled);
        return ti.getRoutes(srcDpid, dstDpid);
    }

    // ******************
    // IOFMessageListener
    // ******************

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return "linkdiscovery".equals(name);
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    @Override
    @SuppressFBWarnings(value="BC_UNCONFIRMED_CAST")
    public Command receive(IOFSwitch sw, OFMessage msg,
                           FloodlightContext cntx) {
        switch (msg.getType()) {
            case FLOW_REMOVED:
                // this is meant for tunnel liveness detection but is currently unused
                return this.flowRemoved(sw, (OFFlowRemoved) msg, cntx);

            case PACKET_IN:
                // this takes the floodlight Topology Manager path
                ctrIncoming.updateCounterNoFlush();
                return this.processPacketInMessage(sw,
                                                   (OFPacketIn) msg, cntx);
            default:
                break;
        }

        return Command.CONTINUE;
    }

    // ***************
    // IHAListener
    // ***************

    private class HAListenerDelegate implements IHAListener {
        @Override
        public void transitionToMaster() {
            role = Role.MASTER;
            log.debug("Re-computing topology due " +
                    "to HA change from SLAVE->MASTER");
            newInstanceTask.reschedule(TOPOLOGY_COMPUTE_INTERVAL_MS,
                                       TimeUnit.MILLISECONDS);
        }

        @Override
        public void controllerNodeIPsChanged(
                                             Map<String, String> curControllerNodeIPs,
                                             Map<String, String> addedControllerNodeIPs,
                                             Map<String, String> removedControllerNodeIPs) {
            // no-op
        }

        @Override
        public String getName() {
            return TopologyManager.this.getName();
        }

        @Override
        public boolean isCallbackOrderingPrereq(HAListenerTypeMarker type,
                                                String name) {
            return "linkdiscovery".equals(name) ||
                    "tunnelmanager".equals(name);
        }

        @Override
        public boolean isCallbackOrderingPostreq(HAListenerTypeMarker type,
                                                 String name) {
            // TODO Auto-generated method stub
            return false;
        }
    }

    // *****************
    // IFloodlightModule
    // *****************

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(ITopologyService.class);
        l.add(IRoutingService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m =
            new HashMap<Class<? extends IFloodlightService>,
                IFloodlightService>();
        // We are the class that implements the service
        m.put(ITopologyService.class, this);
        m.put(IRoutingService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(ILinkDiscoveryService.class);
        l.add(IThreadPoolService.class);
        l.add(IFloodlightProviderService.class);
        l.add(ICounterStoreService.class);
        l.add(IBigDBService.class);
        l.add(ITunnelService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        linkDiscovery = context.getServiceImpl(ILinkDiscoveryService.class);
        threadPool = context.getServiceImpl(IThreadPoolService.class);
        floodlightProvider =
                context.getServiceImpl(IFloodlightProviderService.class);
        debugCounters = context.getServiceImpl(IDebugCounterService.class);
        bigDB = context.getServiceImpl(IBigDBService.class);
        debugEvents = context.getServiceImpl(IDebugEventService.class);

        switchPorts = new HashMap<Long,Set<Short>>();
        switchPortLinks = new HashMap<NodePortTuple, Set<Link>>();
        directLinks = new HashMap<NodePortTuple, Set<Link>>();
        portBroadcastDomainLinks = new HashMap<NodePortTuple, Set<Link>>();
        tunnelPorts = new HashSet<NodePortTuple>();
        topologyAware = new ArrayList<ITopologyListener>();
        ldUpdates = new LinkedBlockingQueue<LDUpdate>();
        haListener = new HAListenerDelegate();
        registerTopologyDebugCounters();
        registerTopologyDebugEvents();
        
        tunnelManager = context.getServiceImpl(ITunnelService.class);
        this.tunnelDetectionQueue = new LinkedBlockingQueue<OrderedNodePair>();
        this.tunnelVerificationQueue = new LinkedBlockingQueue<OrderedNodePair>();
        this.tunnelStatusMap = new ConcurrentHashMap<Long,
                ConcurrentHashMap<Long, TunnelEvent>>();
        tunnelDetectionTime = this.TUNNEL_DETECTION_TIMEOUT_MS;
        tunnelVerificationTime = this.TUNNEL_VERIFICATION_TIMEOUT_MS;

        Map<String, String> configOptions = context.getConfigParams(this);

        String option = configOptions.get("noftrafficspreading");
        if (option != null && option.equalsIgnoreCase("false")) {
            setNOFTrafficSpreading(false);
            log.debug("Non-Openflow traffic spreading option disabled.");
        } else {
            setNOFTrafficSpreading(true);
            log.debug("Non-Openflow traffic spreading option enabled.");
        }

        option = configOptions.get("multipath");
        if (option != null && option.equalsIgnoreCase("false")) {
            multipathEnabled = false;
            log.debug("Multipathing within Openflow domains disabled.");
        } else {
            multipathEnabled = true;
            log.debug("Multipathing within Openflow domains enabled.");
        }
    }

    protected void registerTopologyDebugEvents() throws FloodlightModuleException {
        if (debugEvents == null) {
            debugEvents = new NullDebugEvent();
        }
        try {
            evTopology =
                debugEvents.registerEvent(PACKAGE, "topologyevent",
                                          "Topology Computation",
                                          EventType.ALWAYS_LOG,
                                          TopologyEvent.class, 100);
        } catch (MaxEventsRegistered e) {
            throw new FloodlightModuleException("Max events registered", e);
        }
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        clearCurrentTopology();
        // Initialize role to floodlight provider role.
        this.role = floodlightProvider.getRole();

        ScheduledExecutorService ses = threadPool.getScheduledExecutor();
        newInstanceTask = new SingletonTask(ses, new UpdateTopologyWorker());

        if (role != Role.SLAVE)
            newInstanceTask.reschedule(TOPOLOGY_COMPUTE_INTERVAL_MS,
                                   TimeUnit.MILLISECONDS);

        linkDiscovery.addListener(this);
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addHAListener(this.haListener);

        if (tunnelManager != null) {
            tunnelManager.addListener(this);
        } else {
            log.warn("Cannot listen to tunnel manager as the module is not loaded.");
        }

        floodlightProvider.addInfoProvider("summary", this);
        
        try {
            TopologyResource topologyResource = new TopologyResource(this,
                    linkDiscovery, floodlightProvider, this);
            if (bigDB != null) {
                ServerDataSource controllerDataSource =
                        bigDB.getControllerDataSource();
                // FIXME: Should change the path to be /core/topology.
                controllerDataSource.registerDynamicDataHooksFromObject(
                        new Path("/topology"), topologyResource);
            }
        }
        catch (BigDBException e) {
            log.error("Error attaching BigDB resources: ", e);
        }
        
    }

    private void registerTopologyDebugCounters() throws FloodlightModuleException {
        if (debugCounters == null) {
            log.error("Debug Counter Service not found.");
            debugCounters = new NullDebugCounter();
        }
        try {
            ctrIncoming = debugCounters.registerCounter(PACKAGE, "incoming",
                "All incoming packets seen by this module",
                CounterType.ALWAYS_COUNT);
        } catch (CounterException e) {
            throw new FloodlightModuleException(e.getMessage());
        }
    }

    // ****************
    // Internal methods
    // ****************
    /**
     * If the packet-in switch port is disabled for all data traffic, then
     * the packet will be dropped.  Otherwise, the packet will follow the
     * normal processing chain.
     * @param sw
     * @param pi
     * @param cntx
     * @return
     */
    protected Command dropFilter(long sw, OFPacketIn pi,
                                             FloodlightContext cntx) {
        Command result = Command.CONTINUE;
        short port = pi.getInPort();

        // If the input port is not allowed for data traffic, drop everything.
        // BDDP packets will not reach this stage.
        if (isAllowed(sw, port) == false) {
            if (log.isTraceEnabled()) {
                log.trace("Ignoring packet because of topology " +
                        "restriction on switch={}, port={}", sw, port);
            }
            result = Command.STOP;
        }
        return result;
    }

    /**
     * TODO This method must be moved to a layer below forwarding
     * so that anyone can use it.
     * @param packetData
     * @param sw
     * @param ports
     * @param cntx
     */
    @LogMessageDoc(level="ERROR",
            message="Failed to clear all flows on switch {switch}",
            explanation="An I/O error occured while trying send " +
                    "topology discovery packet",
            recommendation=LogMessageDoc.CHECK_SWITCH)
    public void doMultiActionPacketOut(byte[] packetData, IOFSwitch sw,
                                       Set<Short> ports,
                                       FloodlightContext cntx) {

        if (ports == null) return;
        if (packetData == null || packetData.length <= 0) return;

        OFPacketOut po =
                (OFPacketOut) floodlightProvider.getOFMessageFactory().
                getMessage(OFType.PACKET_OUT);

        List<OFAction> actions = new ArrayList<OFAction>();
        for(short p: ports) {
            actions.add(new OFActionOutput(p, (short) 0));
        }

        // set actions
        po.setActions(actions);
        // set action length
        po.setActionsLength((short) (OFActionOutput.MINIMUM_LENGTH *
                ports.size()));
        // set buffer-id to BUFFER_ID_NONE
        po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        // set in-port to OFPP_NONE
        po.setInPort(OFPort.OFPP_NONE.getValue());

        // set packet data
        po.setPacketData(packetData);

        // compute and set packet length.
        short poLength = (short)(OFPacketOut.MINIMUM_LENGTH +
                po.getActionsLength() +
                packetData.length);

        po.setLength(poLength);

        try {
            //counterStore.updatePktOutFMCounterStore(sw, po);
            if (log.isTraceEnabled()) {
                log.trace("write broadcast packet on switch-id={} " +
                        "interaces={} packet-data={} packet-out={}",
                        new Object[] {sw.getId(), ports, packetData, po});
            }
            sw.write(po, cntx);

        } catch (IOException e) {
            log.error("Failure writing packet out", e);
        }
    }

    /**
     * Get the set of ports to eliminate for sending out BDDP.  The method
     * returns all the ports that are suppressed for link discovery on the
     * switch.
     * packets.
     * @param sid
     * @return
     */
    protected Set<Short> getPortsToEliminateForBDDP(long sid) {
        Set<NodePortTuple> suppressedNptList = linkDiscovery.getSuppressLLDPsInfo();
        if (suppressedNptList == null) return null;

        Set<Short> resultPorts = new HashSet<Short>();
        for(NodePortTuple npt: suppressedNptList) {
            if (npt.getNodeId() == sid) {
                resultPorts.add(npt.getPortId());
            }
        }

        Short tunnelPort = tunnelManager.getTunnelPortNumber(sid);
        if (tunnelPort != null)
            resultPorts.add(tunnelPort);

        return resultPorts;
    }

    /**
     * The BDDP packets are forwarded out of all the ports out of an
     * openflowdomain.  Get all the switches in the same openflow
     * domain as the sw (disabling tunnels).  Then get all the
     * external switch ports and send these packets out.
     * @param sw
     * @param pi
     * @param cntx
     */
    protected void doFloodBDDP(long pinSwitch, OFPacketIn pi,
                               FloodlightContext cntx) {

        TopologyInstance ti = getCurrentInstance(false);

        Set<Long> switches = ti.getSwitchesInOpenflowDomain(pinSwitch);

        for(long sid: switches) {
            IOFSwitch sw = floodlightProvider.getSwitch(sid);
            if (sw == null) continue;
            Collection<Short> enabledPorts = sw.getEnabledPortNumbers();
            if (enabledPorts == null)
                continue;
            Set<Short> ports = new HashSet<Short>();
            ports.addAll(enabledPorts);

            // all the ports known to topology // without tunnels.
            // out of these, we need to choose only those that are
            // broadcast port, otherwise, we should eliminate.
            Set<Short> portsKnownToTopo = ti.getPortsWithLinks(sid);

            if (portsKnownToTopo != null) {
                for(short p: portsKnownToTopo) {
                    NodePortTuple npt =
                            new NodePortTuple(sid, p);
                    if (ti.isBroadcastDomainPort(npt) == false) {
                        ports.remove(p);
                    }
                }
            }

            Set<Short> portsToEliminate = getPortsToEliminateForBDDP(sid);
            if (portsToEliminate != null) {
                ports.removeAll(portsToEliminate);
            }

            // remove the incoming switch port
            if (pinSwitch == sid) {
                ports.remove(pi.getInPort());
            }

            // we have all the switch ports to which we need to broadcast.
            doMultiActionPacketOut(pi.getPacketData(), sw, ports, cntx);
        }

    }

    protected Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi,
                                             FloodlightContext cntx) {

        // get the packet-in switch.
        Ethernet eth =
                IFloodlightProviderService.bcStore.
                get(cntx,IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        if (eth.getPayload() instanceof BSN) {
            BSN bsn = (BSN) eth.getPayload();
            if (bsn == null) return Command.STOP;
            if (bsn.getPayload() == null) return Command.STOP;

            // It could be a packet other than BSN LLDP, therefore
            // continue with the regular processing.
            if (bsn.getPayload() instanceof LLDP == false)
                return Command.CONTINUE;

            doFloodBDDP(sw.getId(), pi, cntx);
            return Command.STOP;
        } else {
            return dropFilter(sw.getId(), pi, cntx);
        }
    }

    /**
     * Updates concerning switch disconnect and port down are not processed.
     * LinkDiscoveryManager is expected to process those messages and send
     * multiple link removed messages.  However, all the updates from
     * LinkDiscoveryManager would be propagated to the listeners of topology.
     */
    @LogMessageDoc(level="ERROR",
            message="Error reading link discovery update.",
            explanation="Unable to process link discovery update",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    public List<LDUpdate> applyUpdates() {
        List<LDUpdate> appliedUpdates = new ArrayList<LDUpdate>();
        LDUpdate update = null;
        while (ldUpdates.peek() != null) {
            try {
                update = ldUpdates.take();
            } catch (Exception e) {
                log.error("Error reading link discovery update.", e);
            }
            if (log.isTraceEnabled()) {
                log.trace("Applying update: {}", update);
            }

            switch (update.getOperation()) {
            case LINK_UPDATED:
                addOrUpdateLink(update.getSrc(), update.getSrcPort(),
                        update.getDst(), update.getDstPort(),
                        update.getType());
                break;
            case LINK_REMOVED:
                removeLink(update.getSrc(), update.getSrcPort(),
                        update.getDst(), update.getDstPort());
                break;
            case SWITCH_UPDATED:
                addOrUpdateSwitch(update.getSrc());
                break;
            case SWITCH_REMOVED:
                removeSwitch(update.getSrc());
                break;
            case TUNNEL_PORT_ADDED:
                addTunnelPort(update.getSrc(), update.getSrcPort());
                break;
            case TUNNEL_PORT_REMOVED:
                removeTunnelPort(update.getSrc(), update.getSrcPort());
                break;
            case PORT_UP: case PORT_DOWN:
                break;
            }
            // Add to the list of applied updates.
            appliedUpdates.add(update);
        }
        return (Collections.unmodifiableList(appliedUpdates));
    }

    /**
     * We model tunnel domain as a switch.  In order to assign a
     * DPID to the switch, we start with a DPID value and check if
     * it is already part of the regular set of switches.  If so,
     * we increment and check again.  This procedure continues
     * until we are able to assign a DPID for the tunnel domain
     * switch.
     */
    private long createTunnelDomain() {
        long tid = 0x00FFFFFFFFFFFFFFL;
        Set<Long> switches = floodlightProvider.getAllSwitchDpids();
        // Get an id for the tunnel domain such that that id does not
        // correspond to an already existing switch.
        while(switches.contains(tid)) {
            tid++;
        }
        return tid;
    }
    
    protected void addOrUpdateSwitch(long sw) {
        if (tunnelManager == null) return;

        // portKnownToTopology is the tunnel port on switch sw as known to
        // topology module.
        NodePortTuple portKnownToTopology = null;
        for (NodePortTuple npt: tunnelPorts) {
            if (npt.getNodeId() == sw) {
                portKnownToTopology = npt;
                break;
            }
        }

        // tunnel manager claims tunnel is not active, remove the tunnel.
        if (!tunnelManager.isTunnelActiveByDpid(sw)) {
            if (portKnownToTopology != null) {
                removeTunnelPort(portKnownToTopology.getNodeId(),portKnownToTopology.getPortId());
            }
            return;
        }

        // now, the tunnel port is active.
        // tp is the tunnel port as seen by the tunnel manager.
        Short tp = tunnelManager.getTunnelPortNumber(sw);

        // tp should not be null, as the tunnel is active.  Still a safety
        // check.
        if (tp == null) {
            if (portKnownToTopology != null) {
                removeTunnelPort(portKnownToTopology.getNodeId(), portKnownToTopology.getPortId());
            }
            return;
        }

        // At this stage, The tunnel is active and the tunnel port is not null.
        if (portKnownToTopology == null) {
            addTunnelPort(sw, tp.shortValue());
            return;
        }

        if (portKnownToTopology.getPortId() != tp.shortValue()) {
            // Remove the old tunnel port and add the new tunnel port.
            removeTunnelPort(sw, portKnownToTopology.getPortId());
            addTunnelPort(sw, tp.shortValue());
        }
    }

    public void addTunnelPort(long sw, short port) {
        NodePortTuple npt = new NodePortTuple(sw, port);
        tunnelPorts.add(npt);
        tunnelPortsUpdated = true;
    }

    public boolean createNewInstance() {
        return createNewInstance("internal");
    }

    /**
     * This function computes a new topology instance.
     * It ignores links connected to all broadcast domain ports
     * and tunnel ports. The method returns if a new instance of
     * topology was created or not.
     */
    protected boolean createNewInstance(String reason) {
        boolean recomputeTopologyFlag = (dtLinksUpdated || tunnelPortsUpdated);

        // Create a new tunnel domain. If the tunnel domain identifier
        // is different from the previous one, topology needs to be
        // recomputed.  We may optimize this logic by checking only
        // if switches were added/updated.
        Long newTunnelDomain = Long.valueOf(createTunnelDomain());
        if (tunnelDomain == null || !tunnelDomain.equals(newTunnelDomain)) {
            tunnelDomain = newTunnelDomain;
            recomputeTopologyFlag = true;
            log.trace("Topology recomputed due to tunnel domain id change.");
        }

        Set<NodePortTuple> blockedPorts = new HashSet<NodePortTuple>();

        Map<NodePortTuple, Set<Link>> linksWithoutTunnels;
        linksWithoutTunnels =
                new HashMap<NodePortTuple, Set<Link>>(switchPortLinks);

        Set<NodePortTuple> broadcastDomainPorts =
                identifyBroadcastDomainPorts();

        Set<BroadcastDomain> bDomains =
                identifyNonOpenflowDomains(broadcastDomainPorts);

        if (broadcastDomains == null ||
                broadcastDomains.equals(bDomains) == false) {
            broadcastDomains = bDomains;
            recomputeTopologyFlag = true;
        }

        // update multipathEnabled in current instance
        if (this.getCurrentInstance() != null)
            this.getCurrentInstance().setMultipathStatus(multipathEnabled);

        // Quit if topology re-computation is not necessary.
        if (!recomputeTopologyFlag) return false;

        // Remove tunnel links
        for (NodePortTuple npt: tunnelPorts) {
            linksWithoutTunnels.remove(npt);
        }

        // First, create a topology instance excluding the tunnel links.
        // There could be a few blocked links, hence ports.  Get the
        // collection of blocked ports from this topology instance.
        //
        // ignore tunnel ports.
        TopologyInstance ntNoTunnels =
                new TopologyInstance(switchPorts,
                                     blockedPorts,
                                     linksWithoutTunnels,
                                     broadcastDomainPorts,
                                     new HashSet<NodePortTuple>(), // no tunnel ports
                                     broadcastDomains,
                                     tunnelDomain,
                                     floodlightProvider,
                                     tunnelManager,
                                     multipathEnabled,
                                     getNOFTrafficSpreading());
        ntNoTunnels.compute();


        // Now including the tunnel ports
        TopologyInstance nt =
                new TopologyInstance(switchPorts,
                                     blockedPorts,
                                     switchPortLinks,
                                     broadcastDomainPorts,
                                     tunnelPorts,
                                     broadcastDomains,
                                     tunnelDomain,
                                     floodlightProvider,
                                     tunnelManager,
                                     multipathEnabled,
                                     getNOFTrafficSpreading());
        nt.compute();

        currentInstanceWithoutTunnels = ntNoTunnels;
        currentInstance = nt;

        TopologyEventInfo topologyInfo =
            new TopologyEventInfo(getL2DomainIds().keySet().size(),
                                  getCurrentInstance(false).getL2DomainIds().keySet().size(),
                                  getExternalPortsMap(),
                                  getTunnelPorts().size());
        evTopology.updateEventWithFlush(new TopologyEvent(reason,
                                                          topologyInfo));
        return true;
    }

    public Map<Long, List<NodePortTuple>> getExternalPortsMap() {
        Set<BroadcastDomain> bDomains = getBroadcastDomains();
        Map<Long, List<NodePortTuple>> result = new HashMap<Long, List<NodePortTuple>>();

        for(BroadcastDomain bd: bDomains) {
            List<NodePortTuple> nptList = new ArrayList<NodePortTuple>();
            for(NodePortTuple npt: bd.getPorts()) {
                nptList.add(npt);
            }
            result.put(bd.getId(), nptList);
        }
        return result;
    }

    /**
     * @author Srinivasan Ramasubramanian
     *
     * This function computes the groups of
     * switch ports that connect to  non-openflow domains. They are
     * grouped together based on whether there's a broadcast
     * leak from one to another or not.  We will currently
     * ignore directionality of the links from the switch ports.
     *
     * Assuming link tuple as undirected, the goal is to simply
     * compute connected components.
     *
     */
    protected Set<BroadcastDomain>
    identifyNonOpenflowDomains(Set<NodePortTuple> broadcastDomainPorts) {
        Set<BroadcastDomain> broadcastDomains = new HashSet<BroadcastDomain>();

        Set<NodePortTuple> visitedNpt = new HashSet<NodePortTuple>();
        // create an queue of NPT to be examined.
        Queue<NodePortTuple> nptQueue = new LinkedList<NodePortTuple>();
        // Do a breadth first search to get all the connected components
        for(NodePortTuple npt: broadcastDomainPorts) {
            if (visitedNpt.contains(npt)) continue;

            BroadcastDomain bd = new BroadcastDomain();
            bd.add(npt);
            bd.setId(broadcastDomains.size()+1);
            broadcastDomains.add(bd);

            visitedNpt.add(npt);
            nptQueue.add(npt);

            while(nptQueue.peek() != null) {
                NodePortTuple currNpt = nptQueue.remove();
                if (switchPortLinks.containsKey(currNpt) == false) continue;
                for(Link l: switchPortLinks.get(currNpt)) {
                    NodePortTuple otherNpt;
                    if (l.getSrc() == currNpt.getNodeId() &&
                            l.getSrcPort() == currNpt.getPortId()) {
                        otherNpt = new NodePortTuple(l.getDst(), l.getDstPort());
                    } else {
                        otherNpt = new NodePortTuple(l.getSrc(), l.getSrcPort());
                    }

                    if (visitedNpt.contains(otherNpt) == false) {
                        nptQueue.add(otherNpt);
                        visitedNpt.add(otherNpt);
                        bd.add(otherNpt);
                    }
                }
            }
        }

        if (broadcastDomains.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("No broadcast domains exist.");
            }
        } else {
            if (log.isTraceEnabled()) {
                StringBuffer bds = new StringBuffer();
                for(BroadcastDomain bd:broadcastDomains) {
                    bds.append(bd);
                    bds.append(" ");
                }
                log.trace("Broadcast domains found in the network: {}", bds);
            }
        }

        return broadcastDomains;
    }

    /**
     *  We expect every switch port to have at most two links.  Both these
     *  links must be unidirectional links connecting to the same switch port.
     *  If not, we will mark this as a broadcast domain port.
     */
    protected Set<NodePortTuple> identifyBroadcastDomainPorts() {

        Set<NodePortTuple> broadcastDomainPorts =
                new HashSet<NodePortTuple>();
        broadcastDomainPorts.addAll(this.portBroadcastDomainLinks.keySet());

        Set<NodePortTuple> additionalNpt =
                new HashSet<NodePortTuple>();

        // Copy switchPortLinks
        Map<NodePortTuple, Set<Link>> spLinks =
                new HashMap<NodePortTuple, Set<Link>>();
        for(NodePortTuple npt: switchPortLinks.keySet()) {
            spLinks.put(npt, new HashSet<Link>(switchPortLinks.get(npt)));
        }

        for(Entry<NodePortTuple,Set<Link>> entry: spLinks.entrySet()) {
            NodePortTuple npt = entry.getKey();
            Set<Link> links = entry.getValue();
            boolean bdPort = false;
            ArrayList<Link> linkArray = new ArrayList<Link>();
            if (links.size() > 2) {
                bdPort = true;
            } else if (links.size() == 2) {
                for(Link l: links) {
                    linkArray.add(l);
                }
                // now, there should be two links in [0] and [1].
                Link l1 = linkArray.get(0);
                Link l2 = linkArray.get(1);

                // check if these two are symmetric.
                if (l1.getSrc() != l2.getDst() ||
                        l1.getSrcPort() != l2.getDstPort() ||
                        l1.getDst() != l2.getSrc() ||
                        l1.getDstPort() != l2.getSrcPort()) {
                    bdPort = true;
                }
            }

            if (bdPort && (broadcastDomainPorts.contains(npt) == false)) {
                additionalNpt.add(npt);
            }
        }

        if (additionalNpt.size() > 0) {
            log.warn("The following switch ports have multiple " +
                    "links incident on them, so these ports will be treated " +
                    " as braodcast domain ports. {}", additionalNpt);

            broadcastDomainPorts.addAll(additionalNpt);
        }
        return broadcastDomainPorts;
    }



    public void informListeners(List<LDUpdate> linkUpdates) {

        if (role != null && role != Role.MASTER)
            return;

        for(int i=0; i<topologyAware.size(); ++i) {
            ITopologyListener listener = topologyAware.get(i);
            listener.topologyChanged(linkUpdates);
        }
    }

    public void addSwitch(long sid) {
        if (switchPorts.containsKey(sid) == false) {
            switchPorts.put(sid, new HashSet<Short>());
        }
    }

    private void addPortToSwitch(long s, short p) {
        addSwitch(s);
        switchPorts.get(s).add(p);
    }

    public void removeSwitch(long sid) {
        // Delete all the links in the switch, switch and all
        // associated data should be deleted.
        if (switchPorts.containsKey(sid) == false) return;

        // Check if any tunnel ports need to be removed.
        for(NodePortTuple npt: tunnelPorts) {
            if (npt.getNodeId() == sid) {
                removeTunnelPort(npt.getNodeId(), npt.getPortId());
            }
        }

        Set<Link> linksToRemove = new HashSet<Link>();
        for(Short p: switchPorts.get(sid)) {
            NodePortTuple n1 = new NodePortTuple(sid, p);
            linksToRemove.addAll(switchPortLinks.get(n1));
        }

        if (linksToRemove.isEmpty()) return;

        for(Link link: linksToRemove) {
            removeLink(link);
        }
    }

    /**
     * Add the given link to the data structure.  Returns true if a link was
     * added.
     * @param s
     * @param l
     * @return
     */
    private boolean addLinkToStructure(Map<NodePortTuple,
                                       Set<Link>> s, Link l) {
        boolean result1 = false, result2 = false;

        NodePortTuple n1 = new NodePortTuple(l.getSrc(), l.getSrcPort());
        NodePortTuple n2 = new NodePortTuple(l.getDst(), l.getDstPort());

        if (s.get(n1) == null) {
            s.put(n1, new HashSet<Link>());
        }
        if (s.get(n2) == null) {
            s.put(n2, new HashSet<Link>());
        }
        result1 = s.get(n1).add(l);
        result2 = s.get(n2).add(l);

        return (result1 || result2);
    }

    /**
     * Delete the given link from the data strucure.  Returns true if the
     * link was deleted.
     * @param s
     * @param l
     * @return
     */
    private boolean removeLinkFromStructure(Map<NodePortTuple,
                                            Set<Link>> s, Link l) {

        boolean result1 = false, result2 = false;
        NodePortTuple n1 = new NodePortTuple(l.getSrc(), l.getSrcPort());
        NodePortTuple n2 = new NodePortTuple(l.getDst(), l.getDstPort());

        if (s.get(n1) != null) {
            result1 = s.get(n1).remove(l);
            if (s.get(n1).isEmpty()) s.remove(n1);
        }
        if (s.get(n2) != null) {
            result2 = s.get(n2).remove(l);
            if (s.get(n2).isEmpty()) s.remove(n2);
        }
        return result1 || result2;
    }

    public void addOrUpdateLink(long srcId, short srcPort, long dstId,
                                short dstPort, LinkType type) {
        Link link = new Link(srcId, srcPort, dstId, dstPort);

        if (type.equals(LinkType.MULTIHOP_LINK)) {
            addPortToSwitch(srcId, srcPort);
            addPortToSwitch(dstId, dstPort);
            addLinkToStructure(switchPortLinks, link);

            addLinkToStructure(portBroadcastDomainLinks, link);
            dtLinksUpdated = removeLinkFromStructure(directLinks, link);
            linksUpdated = true;
        } else if (type.equals(LinkType.DIRECT_LINK)) {
            addPortToSwitch(srcId, srcPort);
            addPortToSwitch(dstId, dstPort);
            addLinkToStructure(switchPortLinks, link);

            addLinkToStructure(directLinks, link);
            removeLinkFromStructure(portBroadcastDomainLinks, link);
            dtLinksUpdated = true;
            linksUpdated = true;
        } else if (type.equals(LinkType.TUNNEL)) {
            addOrUpdateTunnelLink(srcId, srcPort, dstId, dstPort);
        }
    }

    public void removeLink(Link link)  {
        linksUpdated = true;
        dtLinksUpdated = removeLinkFromStructure(directLinks, link);
        removeLinkFromStructure(portBroadcastDomainLinks, link);
        removeLinkFromStructure(switchPortLinks, link);

        NodePortTuple srcNpt =
                new NodePortTuple(link.getSrc(), link.getSrcPort());
        NodePortTuple dstNpt =
                new NodePortTuple(link.getDst(), link.getDstPort());

        // Remove switch ports if there are no links through those switch ports
        if (switchPortLinks.get(srcNpt) == null) {
            if (switchPorts.get(srcNpt.getNodeId()) != null)
                switchPorts.get(srcNpt.getNodeId()).remove(srcNpt.getPortId());
        }
        if (switchPortLinks.get(dstNpt) == null) {
            if (switchPorts.get(dstNpt.getNodeId()) != null)
                switchPorts.get(dstNpt.getNodeId()).remove(dstNpt.getPortId());
        }

        // Remove the node if no ports are present
        if (switchPorts.get(srcNpt.getNodeId())!=null &&
                switchPorts.get(srcNpt.getNodeId()).isEmpty()) {
            switchPorts.remove(srcNpt.getNodeId());
        }
        if (switchPorts.get(dstNpt.getNodeId())!=null &&
                switchPorts.get(dstNpt.getNodeId()).isEmpty()) {
            switchPorts.remove(dstNpt.getNodeId());
        }
    }

    public void removeLink(long srcId, short srcPort,
                           long dstId, short dstPort) {
        Link link = new Link(srcId, srcPort, dstId, dstPort);
        removeLink(link);
    }

    public void clear() {
        switchPorts.clear();
        tunnelPorts.clear();
        switchPortLinks.clear();
        portBroadcastDomainLinks.clear();
        directLinks.clear();
    }

    /**
    * Clears the current topology. Note that this does NOT
    * send out updates.
    */
    public void clearCurrentTopology() {
        this.clear();
        linksUpdated = true;
        dtLinksUpdated = true;
        tunnelPortsUpdated = true;
        createNewInstance("startup");
        lastUpdateTime = new Date();
    }

    /**
     * Getters.  No Setters.
     */
    public Map<Long, Set<Short>> getSwitchPorts() {
        return switchPorts;
    }

    public Map<NodePortTuple, Set<Link>> getSwitchPortLinks() {
        return switchPortLinks;
    }

    public Map<NodePortTuple, Set<Link>> getPortBroadcastDomainLinks() {
        return portBroadcastDomainLinks;
    }

    /**
     * Return tunnel domain identifier.
     * @return
     */
    public Long getTunnelDomainId() {
        return tunnelDomain;
    }
    
    public TopologyInstance getCurrentInstance(boolean tunnelEnabled) {
        if (tunnelEnabled)
            return currentInstance;
        else return this.currentInstanceWithoutTunnels;
    }

    public TopologyInstance getCurrentInstance() {
        return this.getCurrentInstance(true);
    }

    /**
     *  Switch methods
     */
    @Override
    public Set<Short> getPorts(long sw) {
        IOFSwitch iofSwitch = floodlightProvider.getSwitch(sw);
        if (iofSwitch == null) return Collections.emptySet();

        Collection<Short> ofpList = iofSwitch.getEnabledPortNumbers();
        if (ofpList == null) return Collections.emptySet();

        Set<Short> ports = new HashSet<Short>(ofpList);
        Set<Short> qPorts = linkDiscovery.getQuarantinedPorts(sw);
        if (qPorts != null)
            ports.removeAll(qPorts);

        return ports;
    }

    // *****************
    //  Tunnel liveness methods and IBigTopology Service
    // *****************
    private void addToTunnelStatus(TunnelEvent event) {
        long src = event.getSrcDPID();
        long dst = event.getDstDPID();

        // Only add to tunnel status if the switch is
        // known and is in active state. This is to ensure that
        // we will have a valid event in the future that would
        // allow us to remove this entry from the status queue.
        if (floodlightProvider.getSwitch(src) == null ||
                tunnelManager.isTunnelActiveByDpid(src) == false ||
                floodlightProvider.getSwitch(dst) == null ||
                tunnelManager.isTunnelActiveByDpid(dst) == false)
            return;

        tunnelStatusMap.putIfAbsent(src,
                new ConcurrentHashMap<Long, TunnelEvent>());
        tunnelStatusMap.get(src).put(dst, event);

        tunnelStatusMap.putIfAbsent(src,
                new ConcurrentHashMap<Long, TunnelEvent>());
        tunnelStatusMap.get(src).put(dst, event);

        tunnelStatusMap.putIfAbsent(dst,
                new ConcurrentHashMap<Long, TunnelEvent>());
        tunnelStatusMap.get(dst).put(src, event);
    }

    private void removeFromTunnelStatus(OrderedNodePair onp) {
        long src = onp.getSrc();
        long dst = onp.getDst();

        ConcurrentHashMap<Long, TunnelEvent> map;
        map = tunnelStatusMap.get(src);
        if (map != null) {
            map.remove(dst);
        }

        map = tunnelStatusMap.get(dst);
        if (map != null) {
            map.remove(src);
        }
    }

    private void removeFromTunnelStatus(long sw) {
        tunnelStatusMap.remove(sw);
        for(Long othersw: tunnelStatusMap.keySet()) {
            tunnelStatusMap.get(othersw).remove(sw);
        }
    }

    /**
     * The tunnel liveness detection and verification logic works as part
     * of the miscellaneous periodic events.
     */
    protected void handleMiscellaneousPeriodicEvents() {
        tunnelDetectionTime -= TOPOLOGY_COMPUTE_INTERVAL_MS;
        tunnelVerificationTime -= TOPOLOGY_COMPUTE_INTERVAL_MS;

        if (tunnelVerificationTime <= 0) {
            tunnelVerificationTime = this.TUNNEL_VERIFICATION_TIMEOUT_MS;
            Set<OrderedNodePair> npSet = new HashSet<OrderedNodePair>();
            npSet.addAll(tunnelVerificationQueue);
            tunnelVerificationQueue.clear();
            if (!npSet.isEmpty()) {
                for (OrderedNodePair onp: npSet) {
                    TunnelEvent event = new TunnelEvent(onp.getSrc(),
                                                        onp.getDst(),
                                                        TunnelLinkStatus.DOWN);
                    log.warn("Tunnel link failed. src-dpid: {}, dst-dpid: {}",
                                HexString.toHexString(onp.getSrc()),
                                HexString.toHexString(onp.getDst()));

                    addToTunnelStatus(event);
                }
            }
        }

        if (tunnelDetectionTime <= 0) {
            tunnelDetectionTime = this.TUNNEL_DETECTION_TIMEOUT_MS;
            while (tunnelDetectionQueue.peek() != null) {
                OrderedNodePair onp = tunnelDetectionQueue.remove();
                this.verifyTunnelLiveness(onp.getSrc(), onp.getDst());
            }
        }
    }

    public BlockingQueue<OrderedNodePair> getTunnelDetectionQueue() {
        return tunnelDetectionQueue;
    }

    public BlockingQueue<OrderedNodePair> getTunnelVerificationQueue() {
        return tunnelVerificationQueue;
    }

    /**
     *  When a tunnel link addorUpdate event occurs, we need to remove
     *  the corresponding tunnel from the tunnelDetectionQueue as we have
     *  detected the tunnel destination switch port.
     */
    protected void addOrUpdateTunnelLink(long srcId, short srcPort, long dstId,
                                         short dstPort) {
        // Here, we need to remove the link from the check
        // and add the status that the tunnel is up.
        OrderedNodePair onp = new OrderedNodePair(srcId, dstId);
        tunnelDetectionQueue.remove(onp);
        tunnelVerificationQueue.remove(onp);
        removeFromTunnelStatus(onp);
    }

    @Override
    public void detectTunnelSource(long srcDPID, long dstDPID) {

        OrderedNodePair onp = new OrderedNodePair(srcDPID, dstDPID);

        // Add this event to the detection queue only if this event
        // is not already in the detection or verification queues.
        if (!tunnelVerificationQueue.contains(onp) &&
                !tunnelDetectionQueue.contains(onp))
            tunnelDetectionQueue.add(onp);
    }

    @Override
    public void detectTunnelDestination(long srcDPID, long dstDPID) {
        OrderedNodePair onp = new OrderedNodePair(srcDPID, dstDPID);
        tunnelDetectionQueue.remove(onp);
        tunnelVerificationQueue.remove(onp);
        removeFromTunnelStatus(onp);
    }

    @Override
    public void verifyTunnelOnDemand(long srcDPID, long dstDPID) {
        detectTunnelSource(srcDPID, dstDPID);
        detectTunnelSource(dstDPID, srcDPID);
    }

    private void verifyTunnelLiveness(long srcDPID, long dstDPID) {

        if (tunnelManager == null) {
            log.warn("Cannot veirfy tunnel without tunnel manager.");
            return;
        }

        // If the tunnel end-points are not active, there's no point in
        // verifying liveness.
        if (!tunnelManager.isTunnelActiveByDpid(srcDPID)) {
            if (log.isTraceEnabled()) {
                log.trace("Switch {} is not in tunnel active state," +
                        " cannot verify tunnel liveness.", srcDPID);
            }
            return;
        }
        if (!tunnelManager.isTunnelActiveByDpid(dstDPID)) {
            if (log.isTraceEnabled()) {
                log.trace("Switch {} is not in tunnel active state," +
                        " cannot verify tunnel liveness.", dstDPID);
            }
            return;
        }

        // At this point, both endpoints are tunnel active.
        Short srcPort = tunnelManager.getTunnelPortNumber(srcDPID);
        Integer dstIpAddr = tunnelManager.getTunnelIPAddr(dstDPID);

        IOFSwitch iofSwitch = floodlightProvider.getSwitch(srcDPID);
        if (iofSwitch == null) {
            if (log.isTraceEnabled()) {
                log.trace("Cannot send tunnel LLDP as switch object does " +
                        "not exist for DPID {}", srcDPID);
            }
            return;
        }

        // Generate and send an LLDP to the tunnel port of srcDPID
        OFPacketOut po = linkDiscovery.generateLLDPMessage(srcDPID,
                                                           srcPort.shortValue(),
                                                           true, false);

        List<OFAction> actions = new ArrayList<OFAction>();
        short actionsLength = 0;

        // Set the tunnel destination action
        OFActionTunnelDstIP tunnelDstAction =
                new OFActionTunnelDstIP(dstIpAddr.intValue());
        actions.add(tunnelDstAction);
        actionsLength += tunnelDstAction.getLengthU();

        // Set the output port action
        OFActionOutput outputAction = new OFActionOutput();
        outputAction.setPort(srcPort.shortValue());
        actions.add(outputAction);
        actionsLength += outputAction.getLengthU();

        po.setActions(actions);
        po.setActionsLength(actionsLength);
        po.setLengthU(po.getLengthU() + actionsLength);

        try {
            iofSwitch.write(po, null);
            iofSwitch.flush();
            // once the LLDP is written, add the tunnel event to the
            // checkTunnelLivenessQueue
            OrderedNodePair onp = new OrderedNodePair(srcDPID, dstDPID);
            this.tunnelVerificationQueue.add(onp);
        } catch (IOException e) {
            log.error("Failure sending LLDP out port {} on switch {}",
                      new Object[] { srcPort, iofSwitch.getStringId() }, e);
        }
    }

    @Override
    public void clearTunnelLivenessState() {
        this.tunnelStatusMap.clear();
    }

    /**
     * Get all the tunnel events from the hashmap.
     * There could be duplicates as the events are indexed based on
     * src and dst DPIDs. So, it is first put into a set and then
     * changed to a list.
     */
    @Override
    public List<TunnelEvent> getTunnelLivenessState() {
        HashSet<TunnelEvent> eventSet = new HashSet<TunnelEvent>();

        for(Long src: tunnelStatusMap.keySet()) {
            ConcurrentHashMap<Long, TunnelEvent> map =
                    tunnelStatusMap.get(src);
            if (map != null)
                eventSet.addAll(map.values());
        }
        return new ArrayList<TunnelEvent>(eventSet);
    }

    /**
     * Get the tunnel liveness state -- bidirectional -- between a given
     * source destination pair.
     */
    @Override
    public List<TunnelEvent> getTunnelLivenessState(long srcDPID,
                                                    long dstDPID) {
        List<TunnelEvent> eventList = new ArrayList<TunnelEvent>();
        TunnelEvent event;

        event = getTunnelLivenessStateDirectional(srcDPID, dstDPID);
        eventList.add(event);
        event = getTunnelLivenessStateDirectional(dstDPID, srcDPID);
        eventList.add(event);

        return eventList;
    }

    /**
     * Get the status of a specific directed tunnel from
     * srcDPID to dstDPID.
     */
    private TunnelEvent getTunnelLivenessStateDirectional(long srcDPID,
                                                    long dstDPID) {
        OrderedNodePair onp = new OrderedNodePair(srcDPID, dstDPID);
        TunnelEvent event = null;
        if (tunnelStatusMap.containsKey(onp.getSrc())) {
            event = tunnelStatusMap.get(onp.getSrc()).get(onp.getDst());
        }

        if (event == null) {
            // The tunnel event doesn't exist.  This could either mean
            // that one or both switch ports are not tunnel capable/active
            // or the tunnel is up.
            Short srcPort = tunnelManager.getTunnelPortNumber(srcDPID);
            Short dstPort = tunnelManager.getTunnelPortNumber(dstDPID);
            if (srcPort == null || dstPort == null) {
                event = new TunnelEvent(srcDPID, dstDPID,
                                        TunnelLinkStatus.NOT_ENABLED);
            } else if (!tunnelManager.isTunnelActiveByDpid(srcDPID) ||
                    !tunnelManager.isTunnelActiveByDpid(dstDPID)) {
                // As one or both of the endpoints is not in active
                // state, tunnel link is in down state.
                event = new TunnelEvent(srcDPID, dstDPID,
                        TunnelLinkStatus.DOWN);
            }
            else {
                // If no entry is present and the tunnel endpoints are active
                // then the tunnel is up.
                event = new TunnelEvent(srcDPID, dstDPID,
                                        TunnelLinkStatus.UP);
            }
        }
        return event;
    }

    /**
     * Get the timeout to detect if the last hop tunnel flowmod was received
     * or not. The value is in milliseconds.
     * @return
     */
    public int geTunnelDetectionTimeout() {
        return TUNNEL_DETECTION_TIMEOUT_MS;
    }

    /**
     * Set the timetout to detect if the last hop tunnel flowmod was received
     * or not.  The value is in milliseconds.
     * @param time_ms
     */
    public void setTunnelDetectionTimeout(int time_ms) {
        TUNNEL_DETECTION_TIMEOUT_MS = time_ms;
    }

    /**
     * Get the timeout for LLDP reception on tunnel ports.  The value is
     * in milliseconds.
     * @return
     */
    public int getTunnelVerificationTimeout() {
        return TUNNEL_VERIFICATION_TIMEOUT_MS;
    }

    /**
     * Set the timeout for LLDP reception on tunnel ports. The value is
     * in milliseconds.
     * @param time_ms
     */
    public void setTunnelVerificationTimeout(int time_ms) {
        TUNNEL_VERIFICATION_TIMEOUT_MS = time_ms;
    }

    /**
     * To use this method to finally check for tunnel status when flowmods
     * are being removed.
     */
    private Command flowRemoved(IOFSwitch sw, OFFlowRemoved msg,
                                FloodlightContext cntx) {

        /*
        if (tunnelManager == null) return Command.CONTINUE;
        OFMatch match = msg.getMatch();

        long dpid = sw.getId();
        int srcIp = match.getNetworkSource();
        int dstIp = match.getNetworkDestination();

        Long srcDPID = tunnelManager.getSwitchDpid(srcIp);
        Long dstDPID = tunnelManager.getSwitchDpid(dstIp);

        if (srcDPID != null && dstDPID != null && !srcDPID.equals(dstDPID)) {
            if (srcDPID.equals(dpid)) {
                // the traffic is from the tunnel IP to tunnel IP.
                this.detectTunnelSource(srcDPID, dstDPID);
            } else if (dstDPID.equals(dpid)) {
                // the traffic is destined
                this.detectTunnelDestination(srcDPID, dstDPID);
            }
        }
        */
        return Command.CONTINUE;
    }

    public void removeTunnelPort(long sw, short port) {
        NodePortTuple npt = new NodePortTuple(sw, port);
        tunnelPorts.remove(npt);
        tunnelPortsUpdated = true;

        // This call is not present in TopologyManager
        removeFromTunnelStatus(sw);
    }

    // *****************
    //  ITunnelManagerListener methods
    // *****************

    @Override
    public void tunnelPortActive(long dpid, short tunnelPortNumber) {
        LDUpdate ldupdate = new LDUpdate(dpid, tunnelPortNumber,
                                UpdateOperation.TUNNEL_PORT_ADDED);
        ldUpdates.add(ldupdate);
    }

    @Override
    public void tunnelPortInactive(long dpid, short tunnelPortNumber) {
        LDUpdate ldupdate = new LDUpdate(dpid, tunnelPortNumber,
                                         UpdateOperation.TUNNEL_PORT_REMOVED);
        ldUpdates.add(ldupdate);
    }
    
    // **************
    //  IInfoProvider
    // **************

    @Override
    public Map<String, Object> getInfo(String type) {
        if (!"summary".equals(type)) return null;

        Map<String, Object> info = new HashMap<String, Object>();

        info.put("# External Ports", this.getBroadcastDomainPorts().size());
        return info;
    }
}
