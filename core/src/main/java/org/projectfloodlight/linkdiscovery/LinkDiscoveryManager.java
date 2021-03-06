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

package org.projectfloodlight.linkdiscovery;

import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPhysicalPort.OFPortConfig;
import org.openflow.protocol.OFPhysicalPort.OFPortState;
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
import org.projectfloodlight.core.IOFSwitchListener;
import org.projectfloodlight.core.ImmutablePort;
import org.projectfloodlight.core.IFloodlightProviderService.Role;
import org.projectfloodlight.core.annotations.LogMessageCategory;
import org.projectfloodlight.core.annotations.LogMessageDoc;
import org.projectfloodlight.core.annotations.LogMessageDocs;
import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.core.module.FloodlightModuleException;
import org.projectfloodlight.core.module.IFloodlightModule;
import org.projectfloodlight.core.module.IFloodlightService;
import org.projectfloodlight.core.util.SingletonTask;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.IBigDBService;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeSet;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.hook.WatchHook;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.service.Treespace;
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
import org.projectfloodlight.linkdiscovery.ILinkDiscovery.LDUpdate;
import org.projectfloodlight.linkdiscovery.ILinkDiscovery.LinkType;
import org.projectfloodlight.linkdiscovery.ILinkDiscovery.UpdateOperation;
import org.projectfloodlight.notification.INotificationManager;
import org.projectfloodlight.notification.NotificationManagerFactory;
import org.projectfloodlight.packet.BSN;
import org.projectfloodlight.packet.Ethernet;
import org.projectfloodlight.packet.LLDP;
import org.projectfloodlight.packet.LLDPTLV;
import org.projectfloodlight.routing.Link;
import org.projectfloodlight.sync.IStoreClient;
import org.projectfloodlight.sync.IStoreListener;
import org.projectfloodlight.sync.ISyncService;
import org.projectfloodlight.sync.Versioned;
import org.projectfloodlight.sync.ISyncService.Scope;
import org.projectfloodlight.sync.error.ObsoleteVersionException;
import org.projectfloodlight.sync.error.SyncException;
import org.projectfloodlight.sync.error.UnknownStoreException;
import org.projectfloodlight.threadpool.IThreadPoolService;
import org.projectfloodlight.topology.NodePortTuple;
import org.projectfloodlight.tunnel.ITunnelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bigswitch.floodlight.vendor.OFActionMirror;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * This class sends out LLDP messages containing the sending switch's datapath
 * id as well as the outgoing port number. Received LLrescDP messages that match
 * a known switch cause a new LinkTuple to be created according to the invariant
 * rules listed below. This new LinkTuple is also passed to routing if it exists
 * to trigger updates. This class also handles removing links that are
 * associated to switch ports that go down, and switches that are disconnected.
 * Invariants: -portLinks and switchLinks will not contain empty Sets outside of
 * critical sections -portLinks contains LinkTuples where one of the src or dst
 * SwitchPortTuple matches the map key -switchLinks contains LinkTuples where
 * one of the src or dst SwitchPortTuple's id matches the switch id -Each
 * LinkTuple will be indexed into switchLinks for both src.id and dst.id, and
 * portLinks for each src and dst -The updates queue is only added to from
 * within a held write lock
 */
@LogMessageCategory("Network Topology")
public class LinkDiscoveryManager implements IOFMessageListener,
    IOFSwitchListener, ILinkDiscoveryService,
    IFloodlightModule, IInfoProvider, IStoreListener<Link> {
    protected static final Logger log = LoggerFactory.getLogger(LinkDiscoveryManager.class);
    protected static final INotificationManager notifier =
        NotificationManagerFactory.getNotificationManager(LinkDiscoveryManager.class);

    private static final String MODULE_NAME = "linkdiscovery";

    // Event updaters for debug events
    protected IEventUpdater<DirectLinkEvent> evDirectLink;

    protected IFloodlightProviderService floodlightProvider;
    protected IThreadPoolService threadPool;
    protected IDebugCounterService debugCounters;
    protected IDebugEventService debugEvents;
    protected ISyncService syncManager;
    protected ITunnelService tunnelManager;
    protected IBigDBService dbService;
    
    private static final String LINK_DISCOVERY_STORE_NAME = 
            LinkDiscoveryManager.class.getCanonicalName() + ".stateStore";
    private IStoreClient<Link, LinkInfo> linkStoreClient;

    // Role
    protected Role role;

    // LLDP and BDDP fields
    private static final byte[] LLDP_STANDARD_DST_MAC_STRING =
            HexString.fromHexString("01:80:c2:00:00:0e");
    private static final long LINK_LOCAL_MASK = 0xfffffffffff0L;
    private static final long LINK_LOCAL_VALUE = 0x0180c2000000L;
    protected int eventHistorySize = 1024; // in seconds

    // BigSwitch OUI is 5C:16:C7, so 5D:16:C7 is the multicast version
    // private static final String LLDP_BSN_DST_MAC_STRING =
    // "5d:16:c7:00:00:01";
    private static final String LLDP_BSN_DST_MAC_STRING = "ff:ff:ff:ff:ff:ff";

    // Direction TLVs are used to indicate if the LLDPs were sent
    // periodically or in response to a recieved LLDP
    private static final byte TLV_DIRECTION_TYPE = 0x73;
    private static final short TLV_DIRECTION_LENGTH = 1; // 1 byte
    private static final byte TLV_DIRECTION_VALUE_FORWARD[] = { 0x01 };
    private static final byte TLV_DIRECTION_VALUE_REVERSE[] = { 0x02 };
    private static final LLDPTLV forwardTLV = new LLDPTLV().setType(TLV_DIRECTION_TYPE)
                                                           .setLength(TLV_DIRECTION_LENGTH)
                                                           .setValue(TLV_DIRECTION_VALUE_FORWARD);

    private static final LLDPTLV reverseTLV = new LLDPTLV().setType(TLV_DIRECTION_TYPE)
                                                           .setLength(TLV_DIRECTION_LENGTH)
                                                           .setValue(TLV_DIRECTION_VALUE_REVERSE);

    // Link discovery task details.
    protected SingletonTask discoveryTask;
    protected final int DISCOVERY_TASK_INTERVAL = 1;
    protected final int LINK_TIMEOUT = 35; // timeout as part of LLDP process.
    protected final int LLDP_TO_ALL_INTERVAL = 15; // 15 seconds.
    protected long lldpClock = 0;
    // This value is intentionally kept higher than LLDP_TO_ALL_INTERVAL.
    // If we want to identify link failures faster, we could decrease this
    // value to a small number, say 1 or 2 sec.
    protected final int LLDP_TO_KNOWN_INTERVAL = 20; // LLDP frequency for known
                                                     // links

    protected LLDPTLV controllerTLV;
    protected ReentrantReadWriteLock lock;
    int lldpTimeCount = 0;

    /**
     * Flag to indicate if automatic port fast is enabled or not.
     */
    protected boolean autoPortFastFeature = true;

    /**
     * Map from link to the most recent time it was verified functioning
     */
    protected Map<Link, LinkInfo> links;

    /**
     * Map from switch id to a set of all links with it as an endpoint
     */
    protected Map<Long, Set<Link>> switchLinks;

    /**
     * Map from a id:port to the set of links containing it as an endpoint
     */
    protected Map<NodePortTuple, Set<Link>> portLinks;

    protected volatile boolean shuttingDown = false;

    /*
     * topology aware components are called in the order they were added to the
     * the array
     */
    protected ArrayList<ILinkDiscoveryListener> linkDiscoveryAware;
    protected BlockingQueue<LDUpdate> updates;
    protected Thread updatesThread;

    /**
     * List of ports through which LLDP/BDDPs are not sent.
     */
    protected Set<NodePortTuple> suppressLinkDiscovery;

    /**
     * A list of ports that are quarantined for discovering links through them.
     * Data traffic from these ports are not allowed until the ports are
     * released from quarantine.
     */
    protected LinkedBlockingQueue<NodePortTuple> quarantineQueue;
    protected LinkedBlockingQueue<NodePortTuple> maintenanceQueue;
    /**
     * Quarantine task
     */
    protected SingletonTask bddpTask;
    protected final int BDDP_TASK_INTERVAL = 100; // 100 ms.
    protected final int BDDP_TASK_SIZE = 10; // # of ports per iteration

    private static class MACRange {
        long baseMAC;
        int ignoreBits;
    }
    protected Set<MACRange> ignoreMACSet;

    private IHAListener haListener;

    /**
     * Debug Counters
     */
    private IDebugCounter cntSyncStorePuts;
    private IDebugCounter cntSyncStorePutsFailed;
    private IDebugCounter cntSyncStorePutsFailedObsoleteVersion;
    private IDebugCounter cntSyncStoreDeletes;
    private IDebugCounter cntSyncStoreDeletesFailed;
    private IDebugCounter cntSyncStoreDeletesFailedObsoleteVersion;
    private IDebugCounter cntSyncStoreKeysModified;
    private IDebugCounter cntSyncStoreKeysModifiedNullKeys;

    private IDebugCounter ctrQuarantineDrops;
    private IDebugCounter ctrIgnoreSrcMacDrops;
    private IDebugCounter ctrIncoming;
    private IDebugCounter ctrLinkLocalDrops;
    private IDebugCounter ctrLldpEol;

    private final String PACKAGE = LinkDiscoveryManager.class.getPackage().getName();


    //*********************
    // ILinkDiscoveryService
    //*********************

    @Override
    public OFPacketOut generateLLDPMessage(long sw, short port,
                                       boolean isStandard, boolean isReverse) {

        IOFSwitch iofSwitch = floodlightProvider.getSwitch(sw);
        ImmutablePort ofpPort = iofSwitch.getPort(port);

        if (log.isTraceEnabled()) {
            log.trace("Sending LLDP packet out of swich: {}, port: {}",
                      HexString.toHexString(sw), port);
        }

        // using "nearest customer bridge" MAC address for broadest possible
        // propagation
        // through provider and TPMR bridges (see IEEE 802.1AB-2009 and
        // 802.1Q-2011),
        // in particular the Linux bridge which behaves mostly like a provider
        // bridge
        byte[] chassisId = new byte[] { 4, 0, 0, 0, 0, 0, 0 }; // filled in
                                                               // later
        byte[] portId = new byte[] { 2, 0, 0 }; // filled in later
        byte[] ttlValue = new byte[] { 0, 0x78 };
        // OpenFlow OUI - 00-26-E1
        byte[] dpidTLVValue = new byte[] { 0x0, 0x26, (byte) 0xe1, 0, 0, 0,
                                          0, 0, 0, 0, 0, 0 };
        LLDPTLV dpidTLV = new LLDPTLV().setType((byte) 127)
                                       .setLength((short) dpidTLVValue.length)
                                       .setValue(dpidTLVValue);

        byte[] dpidArray = new byte[8];
        ByteBuffer dpidBB = ByteBuffer.wrap(dpidArray);
        ByteBuffer portBB = ByteBuffer.wrap(portId, 1, 2);

        Long dpid = sw;
        dpidBB.putLong(dpid);
        // set the chassis id's value to last 6 bytes of dpid
        System.arraycopy(dpidArray, 2, chassisId, 1, 6);
        // set the optional tlv to the full dpid
        System.arraycopy(dpidArray, 0, dpidTLVValue, 4, 8);

        // TODO: Consider remove this block of code.
        // It's evil to overwrite port object. The the old code always
        // overwrote mac address, we now only overwrite zero macs and
        // log a warning, mostly for paranoia.
        byte[] srcMac = ofpPort.getHardwareAddress();
        byte[] zeroMac = { 0, 0, 0, 0, 0, 0 };
        if (Arrays.equals(srcMac, zeroMac)) {
            log.warn("Port {}/{} has zero hareware address"
                             + "overwrite with lower 6 bytes of dpid",
                     HexString.toHexString(dpid), ofpPort.getPortNumber());
            System.arraycopy(dpidArray, 2, srcMac, 0, 6);
        }

        // set the portId to the outgoing port
        portBB.putShort(port);
        if (log.isTraceEnabled()) {
            log.trace("Sending LLDP out of interface: {}/{}",
                      HexString.toHexString(sw), port);
        }

        LLDP lldp = new LLDP();
        lldp.setChassisId(new LLDPTLV().setType((byte) 1)
                                       .setLength((short) chassisId.length)
                                       .setValue(chassisId));
        lldp.setPortId(new LLDPTLV().setType((byte) 2)
                                    .setLength((short) portId.length)
                                    .setValue(portId));
        lldp.setTtl(new LLDPTLV().setType((byte) 3)
                                 .setLength((short) ttlValue.length)
                                 .setValue(ttlValue));
        lldp.getOptionalTLVList().add(dpidTLV);

        // Add the controller identifier to the TLV value.
        lldp.getOptionalTLVList().add(controllerTLV);
        if (isReverse) {
            lldp.getOptionalTLVList().add(reverseTLV);
        } else {
            lldp.getOptionalTLVList().add(forwardTLV);
        }

        Ethernet ethernet;
        if (isStandard) {
            ethernet = new Ethernet().setSourceMACAddress(ofpPort.getHardwareAddress())
                                     .setDestinationMACAddress(LLDP_STANDARD_DST_MAC_STRING)
                                     .setEtherType(Ethernet.TYPE_LLDP);
            ethernet.setPayload(lldp);
        } else {
            BSN bsn = new BSN(BSN.BSN_TYPE_BDDP);
            bsn.setPayload(lldp);

            ethernet = new Ethernet().setSourceMACAddress(ofpPort.getHardwareAddress())
                                     .setDestinationMACAddress(LLDP_BSN_DST_MAC_STRING)
                                     .setEtherType(Ethernet.TYPE_BSN);
            ethernet.setPayload(bsn);
        }

        // serialize and wrap in a packet out
        byte[] data = ethernet.serialize();
        OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory()
                                                         .getMessage(OFType.PACKET_OUT);
        po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        po.setInPort(OFPort.OFPP_NONE);

        // set data and data length
        po.setLengthU(OFPacketOut.MINIMUM_LENGTH + data.length);
        po.setPacketData(data);

        return po;
    }

    /**
     * Get the LLDP sending period in seconds.
     *
     * @return LLDP sending period in seconds.
     */
    public int getLldpFrequency() {
        return LLDP_TO_KNOWN_INTERVAL;
    }

    /**
     * Get the LLDP timeout value in seconds
     *
     * @return LLDP timeout value in seconds
     */
    public int getLldpTimeout() {
        return LINK_TIMEOUT;
    }

    @Override
    public Map<NodePortTuple, Set<Link>> getPortLinks() {
        return portLinks;
    }

    @Override
    public Set<NodePortTuple> getSuppressLLDPsInfo() {
        return suppressLinkDiscovery;
    }

    /**
     * Add a switch port to the suppressed LLDP list. Remove any known links on
     * the switch port.
     */
    @Override
    public void addToSuppressLLDPs(long sw, short port) {
        NodePortTuple npt = new NodePortTuple(sw, port);
        this.suppressLinkDiscovery.add(npt);
        deleteLinksOnPort(npt, "LLDP suppressed.");
    }

    /**
     * Remove a switch port from the suppressed LLDP list. Discover links on
     * that switchport.
     */
    @Override
    public void removeFromSuppressLLDPs(long sw, short port) {
        NodePortTuple npt = new NodePortTuple(sw, port);
        this.suppressLinkDiscovery.remove(npt);
        discover(npt);
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    @Override
    public boolean isTunnelPort(long sw, short port) {
        if (tunnelManager == null) return false;
        Short tunnelPort = tunnelManager.getTunnelPortNumber(sw);
        if (tunnelPort == null || tunnelPort.shortValue() != port)
            return false;
        return true;
    }

    @Override
    public ILinkDiscovery.LinkType getLinkType(Link lt, LinkInfo info) {

        if (lt == null)
            return ILinkDiscovery.LinkType.INVALID_LINK;

        IOFSwitch srcSw = floodlightProvider.getSwitch(lt.getSrc());
        IOFSwitch dstSw = floodlightProvider.getSwitch(lt.getDst());

        if (srcSw == null || dstSw == null)
            return ILinkDiscovery.LinkType.INVALID_LINK;

        if (isTunnelPort(lt.getSrc(), lt.getSrcPort()) ||
                isTunnelPort(lt.getDst(), lt.getDstPort())) {
            return ILinkDiscovery.LinkType.TUNNEL;
        }

        if (info == null) return ILinkDiscovery.LinkType.INVALID_LINK;

        if (info.getUnicastValidTime() != null) {
            return ILinkDiscovery.LinkType.DIRECT_LINK;
        } else if (info.getMulticastValidTime()  != null) {
            return ILinkDiscovery.LinkType.MULTIHOP_LINK;
        }
        return ILinkDiscovery.LinkType.INVALID_LINK;
    }

    @Override
    public Set<Short> getQuarantinedPorts(long sw) {
        Set<Short> qPorts = new HashSet<Short>();

        Iterator<NodePortTuple> iter = quarantineQueue.iterator();
        while (iter.hasNext()) {
            NodePortTuple npt = iter.next();
            if (npt.getNodeId() == sw) {
                qPorts.add(npt.getPortId());
            }
        }
        return qPorts;
    }

    @Override
    public Map<Long, Set<Link>> getSwitchLinks() {
        return this.switchLinks;
    }

    @Override
    public void addMACToIgnoreList(long mac, int ignoreBits) {
        MACRange range = new MACRange();
        range.baseMAC = mac;
        range.ignoreBits = ignoreBits;
        ignoreMACSet.add(range);
    }

    @Override
    public boolean isAutoPortFastFeature() {
        return autoPortFastFeature;
    }

    @Override
    public void setAutoPortFastFeature(boolean autoPortFastFeature) {
        this.autoPortFastFeature = autoPortFastFeature;
    }

    @Override
    public void addListener(ILinkDiscoveryListener listener) {
        linkDiscoveryAware.add(listener);
    }

    @Override
    public Map<Link, LinkInfo> getLinks() {
        lock.readLock().lock();
        Map<Link, LinkInfo> result;
        try {
            result = new HashMap<Link, LinkInfo>(links);
        } finally {
            lock.readLock().unlock();
        }
        return result;
    }

    @Override
    public LinkInfo getLinkInfo(Link link) {
        lock.readLock().lock();
        LinkInfo linkInfo = links.get(link);
        LinkInfo retLinkInfo = null;
        if (linkInfo != null) {
            retLinkInfo  = new LinkInfo(linkInfo);
        }
        lock.readLock().unlock();
        return retLinkInfo;
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    //*********************
    //   OFMessage Listener
    //*********************

    @Override
    @SuppressFBWarnings(value="BC_UNCONFIRMED_CAST")
    public Command receive(IOFSwitch sw, OFMessage msg,
                           FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                ctrIncoming.updateCounterNoFlush();
                return this.handlePacketIn(sw.getId(), (OFPacketIn) msg,
                                           cntx);
            default:
                break;
        }
        return Command.CONTINUE;
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    //***********************************
    //  Internal Methods - Packet-in Processing Related
    //***********************************

    protected Command handlePacketIn(long sw, OFPacketIn pi,
                                     FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                           IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        if (eth.getPayload() instanceof BSN) {
            BSN bsn = (BSN) eth.getPayload();
            if (bsn == null) return Command.STOP;
            if (bsn.getPayload() == null) return Command.STOP;
            // It could be a packet other than BSN LLDP, therefore
            // continue with the regular processing.
            if (bsn.getPayload() instanceof LLDP == false)
                return Command.CONTINUE;
            return handleLldp((LLDP) bsn.getPayload(), sw, pi.getInPort(), false, cntx);
        } else if (eth.getPayload() instanceof LLDP) {
            return handleLldp((LLDP) eth.getPayload(), sw, pi.getInPort(), true, cntx);
        } else if (eth.getEtherType() < 1500) {
            long destMac = eth.getDestinationMAC().toLong();
            if ((destMac & LINK_LOCAL_MASK) == LINK_LOCAL_VALUE) {
                ctrLinkLocalDrops.updateCounterNoFlush();
                if (log.isTraceEnabled()) {
                    log.trace("Ignoring packet addressed to 802.1D/Q "
                              + "reserved address.");
                }
                return Command.STOP;
            }
        }

        if (ignorePacketInFromSource(eth.getSourceMAC().toLong())) {
            ctrIgnoreSrcMacDrops.updateCounterNoFlush();
            return Command.STOP;
        }

        // If packet-in is from a quarantine port, stop processing.
        NodePortTuple npt = new NodePortTuple(sw, pi.getInPort());
        if (quarantineQueue.contains(npt)) {
            ctrQuarantineDrops.updateCounterNoFlush();
            return Command.STOP;
        }

        return Command.CONTINUE;
    }

    private boolean ignorePacketInFromSource(long srcMAC) {
        Iterator<MACRange> it = ignoreMACSet.iterator();
        while (it.hasNext()) {
            MACRange range = it.next();
            long mask = ~0;
            if (range.ignoreBits >= 0 && range.ignoreBits <= 48) {
                mask = mask << range.ignoreBits;
                if ((range.baseMAC & mask) == (srcMAC & mask)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Command handleLldp(LLDP lldp, long sw, short inPort,
                               boolean isStandard, FloodlightContext cntx) {
        // If LLDP is suppressed on this port, ignore received packet as well
        IOFSwitch iofSwitch = floodlightProvider.getSwitch(sw);

        if (!isIncomingDiscoveryAllowed(sw, inPort, isStandard))
            return Command.STOP;

        // If this is a malformed LLDP exit
        if (lldp.getPortId() == null || lldp.getPortId().getLength() != 3) {
            return Command.STOP;
        }

        long myId = ByteBuffer.wrap(controllerTLV.getValue()).getLong();
        long otherId = 0;
        boolean myLLDP = false;
        Boolean isReverse = null;

        ByteBuffer portBB = ByteBuffer.wrap(lldp.getPortId().getValue());
        portBB.position(1);

        Short remotePort = portBB.getShort();
        IOFSwitch remoteSwitch = null;

        // Verify this LLDP packet matches what we're looking for
        for (LLDPTLV lldptlv : lldp.getOptionalTLVList()) {
            if (lldptlv.getType() == 127 && lldptlv.getLength() == 12
                && lldptlv.getValue()[0] == 0x0
                && lldptlv.getValue()[1] == 0x26
                && lldptlv.getValue()[2] == (byte) 0xe1
                && lldptlv.getValue()[3] == 0x0) {
                ByteBuffer dpidBB = ByteBuffer.wrap(lldptlv.getValue());
                remoteSwitch = floodlightProvider.getSwitch(dpidBB.getLong(4));
            } else if (lldptlv.getType() == 12 && lldptlv.getLength() == 8) {
                otherId = ByteBuffer.wrap(lldptlv.getValue()).getLong();
                if (myId == otherId) myLLDP = true;
            } else if (lldptlv.getType() == TLV_DIRECTION_TYPE
                       && lldptlv.getLength() == TLV_DIRECTION_LENGTH) {
                if (lldptlv.getValue()[0] == TLV_DIRECTION_VALUE_FORWARD[0])
                    isReverse = false;
                else if (lldptlv.getValue()[0] == TLV_DIRECTION_VALUE_REVERSE[0])
                    isReverse = true;
            }
        }

        if (myLLDP == false) {
            // This is not the LLDP sent by this controller.
            // If the LLDP message has multicast bit set, then we need to
            // broadcast the packet as a regular packet (after checking IDs)
            if (isStandard) {
                if (log.isTraceEnabled()) {
                    log.trace("Got a standard LLDP=[{}] that was not sent by" +
                              " this controller. Not fowarding it.", lldp.toString());
                }
                return Command.STOP;
            } else if (myId < otherId) {
                if (log.isTraceEnabled()) {
                    log.trace("Getting BDDP packets from a different controller"
                              + "and letting it go through normal processing chain.");
                }
                return Command.CONTINUE;
            }
            return Command.STOP;
        }

        if (remoteSwitch == null) {
            // Ignore LLDPs not generated by Floodlight, or from a switch that
            // has recently
            // disconnected, or from a switch connected to another Floodlight
            // instance
            if (log.isTraceEnabled()) {
                log.trace("Received LLDP from remote switch not connected to the controller");
            }
            return Command.STOP;
        }

        if (!remoteSwitch.portEnabled(remotePort)) {
            if (log.isTraceEnabled()) {
                log.trace("Ignoring link with disabled source port: switch {} port {} {}",
                          new Object[] { remoteSwitch.getStringId(),
                                         remotePort,
                                         remoteSwitch.getPort(remotePort)});
            }
            return Command.STOP;
        }
        if (suppressLinkDiscovery.contains(new NodePortTuple(
                                                             remoteSwitch.getId(),
                                                             remotePort))) {
            if (log.isTraceEnabled()) {
                log.trace("Ignoring link with suppressed src port: switch {} port {} {}",
                          new Object[] { remoteSwitch.getStringId(),
                                         remotePort,
                                         remoteSwitch.getPort(remotePort)});
            }
            return Command.STOP;
        }
        if (!iofSwitch.portEnabled(inPort)) {
            if (log.isTraceEnabled()) {
                log.trace("Ignoring link with disabled dest port: switch {} port {} {}",
                          new Object[] { HexString.toHexString(sw),
                                         inPort,
                                         iofSwitch.getPort(inPort)});
            }
            return Command.STOP;
        }

        // Store the time of update to this link, and push it out to
        // routingEngine
        Link lt = new Link(remoteSwitch.getId(), remotePort,
                           iofSwitch.getId(), inPort);

        if (!isLinkAllowed(lt.getSrc(), lt.getSrcPort(),
                           lt.getDst(), lt.getDstPort()))
            return Command.STOP;

        // Continue only if link is allowed.
        Long lastLldpTime = null;
        Long lastBddpTime = null;

        Long firstSeenTime = System.currentTimeMillis();

        if (isStandard)
            lastLldpTime = System.currentTimeMillis();
        else
            lastBddpTime = System.currentTimeMillis();

        LinkInfo newLinkInfo = new LinkInfo(firstSeenTime, lastLldpTime,
                                            lastBddpTime);

        addOrUpdateLink(lt, newLinkInfo);

        // Check if reverse link exists.
        // If it doesn't exist and if the forward link was seen
        // first seen within a small interval, send probe on the
        // reverse link.
        newLinkInfo = links.get(lt);
        if (newLinkInfo != null && isStandard && isReverse == false) {
            Link reverseLink = new Link(lt.getDst(), lt.getDstPort(),
                                        lt.getSrc(), lt.getSrcPort());
            LinkInfo reverseInfo = links.get(reverseLink);
            if (reverseInfo == null) {
                // the reverse link does not exist.
                if (newLinkInfo.getFirstSeenTime() > System.currentTimeMillis()
                                                     - LINK_TIMEOUT) {
                    this.sendDiscoveryMessage(lt.getDst(), lt.getDstPort(),
                                              isStandard, true);
                }
            }
        }

        // If the received packet is a BDDP packet, then create a reverse BDDP
        // link as well.
        if (!isStandard) {
            Link reverseLink = new Link(lt.getDst(), lt.getDstPort(),
                                        lt.getSrc(), lt.getSrcPort());

            // srcPortState and dstPort state are reversed.
            LinkInfo reverseInfo = new LinkInfo(firstSeenTime, lastLldpTime,
                                                lastBddpTime);

            addOrUpdateLink(reverseLink, reverseInfo);
        }

        // Remove the node ports from the quarantine and maintenance queues.
        NodePortTuple nptSrc = new NodePortTuple(lt.getSrc(),
                                                 lt.getSrcPort());
        NodePortTuple nptDst = new NodePortTuple(lt.getDst(),
                                                 lt.getDstPort());
        removeFromQuarantineQueue(nptSrc);
        removeFromMaintenanceQueue(nptSrc);
        removeFromQuarantineQueue(nptDst);
        removeFromMaintenanceQueue(nptDst);

        // Consume this message
        ctrLldpEol.updateCounterNoFlush();
        return Command.STOP;
    }

    //***********************************
    //  Internal Methods - Port Status/ New Port Processing Related
    //***********************************
    /**
     * Process a new port. If link discovery is disabled on the port, then do
     * nothing. If autoportfast feature is enabled and the port is a fast port,
     * then do nothing. Otherwise, send LLDP message. Add the port to
     * quarantine.
     *
     * @param sw
     * @param p
     */
    private void processNewPort(long sw, short p) {
        if (isLinkDiscoverySuppressed(sw, p)) {
            // Do nothing as link discovery is suppressed.
            return;
        }

        IOFSwitch iofSwitch = floodlightProvider.getSwitch(sw);
        if (iofSwitch == null) return;

        if (autoPortFastFeature && iofSwitch.isFastPort(p)) {
            // Do nothing as the port is a fast port.
            return;
        }
        NodePortTuple npt = new NodePortTuple(sw, p);
        discover(sw, p);
        // if it is not a fast port, add it to quarantine.
        if (!iofSwitch.isFastPort(p)) {
            addToQuarantineQueue(npt);
        } else {
            // Add to maintenance queue to ensure that BDDP packets
            // are sent out.
            addToMaintenanceQueue(npt);
        }
    }

    //***********************************
    //  Internal Methods - Discovery Related
    //***********************************

    @LogMessageDoc(level = "ERROR",
                   message = "Error in link discovery updates loop",
                   explanation = "An unknown error occured while dispatching "
                                 + "link update notifications",
                   recommendation = LogMessageDoc.GENERIC_ACTION)
    private void doUpdatesThread() throws InterruptedException {
        do {
            LDUpdate update = updates.take();
            List<LDUpdate> updateList = new ArrayList<LDUpdate>();
            updateList.add(update);

            // Add all the pending updates to the list.
            while (updates.peek() != null) {
                updateList.add(updates.remove());
            }

            if (linkDiscoveryAware != null) {
                if (log.isTraceEnabled()) {
                    log.trace("Dispatching link discovery update {} {} {} {} {} for {}",
                              new Object[] {
                                            update.getOperation(),
                                            HexString.toHexString(update.getSrc()),
                                            update.getSrcPort(),
                                            HexString.toHexString(update.getDst()),
                                            update.getDstPort(),
                                            linkDiscoveryAware });
                }
                try {
                    for (ILinkDiscoveryListener lda : linkDiscoveryAware) { // order
                        // maintained
                        lda.linkDiscoveryUpdate(updateList);
                    }
                } catch (Exception e) {
                    log.error("Error in link discovery updates loop", e);
                }
            }
        } while (updates.peek() != null);
    }

    protected boolean isLinkDiscoverySuppressed(long sw, short portNumber) {
        return this.suppressLinkDiscovery.contains(new NodePortTuple(sw,
                                                                     portNumber));
    }

    protected void discoverLinks() {

        // timeout known links.
        timeoutLinks();

        // increment LLDP clock
        lldpClock = (lldpClock + 1) % LLDP_TO_ALL_INTERVAL;

        if (lldpClock == 0) {
            if (log.isTraceEnabled())
                log.trace("Sending LLDP out on all ports.");
            discoverOnAllPorts();
        }
    }

    /**
     * Quarantine Ports.
     */
    protected class QuarantineWorker implements Runnable {
        @Override
        public void run() {
            try {
                processBDDPLists();
            } catch (Exception e) {
                log.error("Error in quarantine worker thread", e);
            } finally {
                bddpTask.reschedule(BDDP_TASK_INTERVAL,
                                    TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Add a switch port to the quarantine queue. Schedule the quarantine task
     * if the quarantine queue was empty before adding this switch port.
     *
     * @param npt
     */
    protected void addToQuarantineQueue(NodePortTuple npt) {
        if (quarantineQueue.contains(npt) == false)
                                                   quarantineQueue.add(npt);
    }

    /**
     * Remove a switch port from the quarantine queue.
     */
    protected void removeFromQuarantineQueue(NodePortTuple npt) {
        // Remove all occurrences of the node port tuple from the list.
        while (quarantineQueue.remove(npt))
            ;
    }

    /**
     * Add a switch port to maintenance queue.
     *
     * @param npt
     */
    protected void addToMaintenanceQueue(NodePortTuple npt) {
        // TODO We are not checking if the switch port tuple is already
        // in the maintenance list or not. This will be an issue for
        // really large number of switch ports in the network.
        if (maintenanceQueue.contains(npt) == false)
                                                    maintenanceQueue.add(npt);
    }

    /**
     * Remove a switch port from maintenance queue.
     *
     * @param npt
     */
    protected void removeFromMaintenanceQueue(NodePortTuple npt) {
        // Remove all occurrences of the node port tuple from the queue.
        while (maintenanceQueue.remove(npt))
            ;
    }

    /**
     * This method processes the quarantine list in bursts. The task is at most
     * once per BDDP_TASK_INTERVAL. One each call, BDDP_TASK_SIZE number of
     * switch ports are processed. Once the BDDP packets are sent out through
     * the switch ports, the ports are removed from the quarantine list.
     */
    protected void processBDDPLists() {
        int count = 0;
        Set<NodePortTuple> nptList = new HashSet<NodePortTuple>();

        while (count < BDDP_TASK_SIZE && quarantineQueue.peek() != null) {
            NodePortTuple npt;
            npt = quarantineQueue.remove();
            sendDiscoveryMessage(npt.getNodeId(), npt.getPortId(), false,
                                 false);
            nptList.add(npt);
            count++;
        }

        count = 0;
        while (count < BDDP_TASK_SIZE && maintenanceQueue.peek() != null) {
            NodePortTuple npt;
            npt = maintenanceQueue.remove();
            sendDiscoveryMessage(npt.getNodeId(), npt.getPortId(), false,
                                 false);
            count++;
        }

        for (NodePortTuple npt : nptList) {
            generateSwitchPortStatusUpdate(npt.getNodeId(), npt.getPortId());
        }
    }

    private void generateSwitchPortStatusUpdate(long sw, short port) {
        UpdateOperation operation;

        IOFSwitch iofSwitch = floodlightProvider.getSwitch(sw);
        if (iofSwitch == null) return;

        ImmutablePort p = iofSwitch.getPort(port);
        if (p == null) return;
        OFPhysicalPort ofp = p.toOFPhysicalPort();

        int srcPortState = ofp.getState();
        boolean portUp = ((srcPortState & OFPortState.OFPPS_STP_MASK.getValue())
                               != OFPortState.OFPPS_STP_BLOCK.getValue());

        if (portUp)
            operation = UpdateOperation.PORT_UP;
        else
            operation = UpdateOperation.PORT_DOWN;

        updates.add(new LDUpdate(sw, port, operation));
    }

    protected void discover(NodePortTuple npt) {
        discover(npt.getNodeId(), npt.getPortId());
    }

    protected void discover(long sw, short port) {
        sendDiscoveryMessage(sw, port, true, false);
    }

    /**
     * Check if incoming discovery messages are enabled or not.
     * @param sw
     * @param port
     * @param isStandard
     * @return
     */
    protected boolean isIncomingDiscoveryAllowed(long sw, short port,
                                                 boolean isStandard) {

        if (isLinkDiscoverySuppressed(sw, port)) {
            /* Do not process LLDPs from this port as suppressLLDP is set */
            return false;
        }

        IOFSwitch iofSwitch = floodlightProvider.getSwitch(sw);
        if (iofSwitch == null) {
            return false;
        }

        if (port == OFPort.OFPP_LOCAL.getValue()) return false;

        ImmutablePort ofpPort = iofSwitch.getPort(port);
        if (ofpPort == null) {
            if (log.isTraceEnabled()) {
                log.trace("Null physical port. sw={}, port={}",
                          HexString.toHexString(sw), port);
            }
            return false;
        }

        return true;
    }

    /**
     * Check if outgoing discovery messages are enabled or not.
     * @param sw
     * @param port
     * @param isStandard
     * @param isReverse
     * @return
     */
    protected boolean isOutgoingDiscoveryAllowed(long sw, short port,
                                                 boolean isStandard,
                                                 boolean isReverse) {
        if (isLinkDiscoverySuppressed(sw, port)) {
            /* Dont send LLDPs out of this port as suppressLLDP is set */
            return false;
        }

        IOFSwitch iofSwitch = floodlightProvider.getSwitch(sw);
        if (iofSwitch == null) {
            return false;
        }

        if (port == OFPort.OFPP_LOCAL.getValue()) return false;

        ImmutablePort ofpPort = iofSwitch.getPort(port);
        if (ofpPort == null) {
            if (log.isTraceEnabled()) {
                log.trace("Null physical port. sw={}, port={}",
                          HexString.toHexString(sw), port);
            }
            return false;
        }

        // For fast ports, do not send forward LLDPs or BDDPs.
        if (!isReverse && isAutoPortFastFeature() && iofSwitch.isFastPort(port))
            return false;

        // if overlay bind mode and port mirror is not enabled, do not send LLDPs or BDDPs.
        if ((iofSwitch.getActions() == 0) &&
            !ofpPort.getConfig().contains(OFPortConfig.OFPPC_BSN_MIRROR_DEST)) {
            return false;
        }

        // if it is a tunnel port, then do not send LLDPs.
        if (tunnelManager != null &&
            tunnelManager.getTunnelPortNumber(sw) != null &&
            tunnelManager.getTunnelPortNumber(sw).shortValue() == port)
            return false;
        
        return true;
    }

    /**
     * Get the actions for packet-out corresponding to a specific port.
     * This is a placeholder for adding actions if any port-specific
     * actions are desired.  The default action is simply to output to
     * the given port.
     * @param port
     * @return
     */
    protected List<OFAction> getDiscoveryActions (IOFSwitch sw, OFPhysicalPort port){
        // set actions
        List<OFAction> actions = new ArrayList<OFAction>();

        // if overlay bind mode and port mirror is enabled, set the action to
        // mirror
        if ((sw.getActions() == 0) && (port.getConfig() == 0x80000000)) {
            actions.add(new OFActionMirror(port.getPortNumber()));
        } else {
            actions.add(new OFActionOutput(port.getPortNumber(), (short) 0));
        }
        return actions;
    }

    /**
     * Send link discovery message out of a given switch port. The discovery
     * message may be a standard LLDP or a modified LLDP, where the dst mac
     * address is set to :ff. TODO: The modified LLDP will updated in the future
     * and may use a different eth-type.
     *
     * @param sw
     * @param port
     * @param isStandard
     *            indicates standard or modified LLDP
     * @param isReverse
     *            indicates whether the LLDP was sent as a response
     */
    @LogMessageDoc(level = "ERROR",
                   message = "Failure sending LLDP out port {port} on switch {switch}",
                   explanation = "An I/O error occured while sending LLDP message "
                                 + "to the switch.",
                   recommendation = LogMessageDoc.CHECK_SWITCH)
    protected void sendDiscoveryMessage(long sw, short port,
                                        boolean isStandard, boolean isReverse) {

        // Takes care of all checks including null pointer checks.
        if (!isOutgoingDiscoveryAllowed(sw, port, isStandard, isReverse))
            return;

        IOFSwitch iofSwitch = floodlightProvider.getSwitch(sw);
        OFPhysicalPort ofpPort = iofSwitch.getPort(port).toOFPhysicalPort();

        if (log.isTraceEnabled()) {
            log.trace("Sending LLDP packet out of swich: {}, port: {}",
                      HexString.toHexString(sw), port);
        }
        OFPacketOut po = generateLLDPMessage(sw, port, isStandard, isReverse);

        // Add actions
        List<OFAction> actions = getDiscoveryActions(iofSwitch, ofpPort);
        po.setActions(actions);
        short  actionLength = 0;
        Iterator <OFAction> actionIter = actions.iterator();
        while (actionIter.hasNext()) {
            actionLength += actionIter.next().getLength();
        }
        po.setActionsLength(actionLength);

        // po already has the minimum length + data length set
        // simply add the actions length to this.
        po.setLengthU(po.getLengthU() + po.getActionsLength());

        // send
        try {
            iofSwitch.write(po, null);
            iofSwitch.flush();
        } catch (IOException e) {
            log.error("Failure sending LLDP out port {} on switch {}",
                      new Object[] { port, iofSwitch.getStringId() }, e);
        }
    }

    /**
     * Send LLDPs to all switch-ports
     */
    protected void discoverOnAllPorts() {
        if (log.isTraceEnabled()) {
            log.trace("Sending LLDP packets out of all the enabled ports");
        }
        // Send standard LLDPs
        for (long sw : floodlightProvider.getAllSwitchDpids()) {
            IOFSwitch iofSwitch = floodlightProvider.getSwitch(sw);
            if (iofSwitch == null) continue;
            if (iofSwitch.getEnabledPorts() != null) {
                for (ImmutablePort ofp : iofSwitch.getEnabledPorts()) {
                    if (isLinkDiscoverySuppressed(sw, ofp.getPortNumber()))
                                                                           continue;
                    if (autoPortFastFeature
                        && iofSwitch.isFastPort(ofp.getPortNumber()))
                                                                     continue;

                    // sends forward LLDP only non-fastports.
                    sendDiscoveryMessage(sw, ofp.getPortNumber(), true,
                                         false);

                    // If the switch port is not alreayd in the maintenance
                    // queue, add it.
                    NodePortTuple npt = new NodePortTuple(
                                                          sw,
                                                          ofp.getPortNumber());
                    addToMaintenanceQueue(npt);
                }
            }
        }
    }

    protected UpdateOperation getUpdateOperation(int srcPortState,
                                                 int dstPortState) {
        boolean added = (((srcPortState & OFPortState.OFPPS_STP_MASK.getValue())
                                != OFPortState.OFPPS_STP_BLOCK.getValue()) &&
                          ((dstPortState & OFPortState.OFPPS_STP_MASK.getValue())
                                != OFPortState.OFPPS_STP_BLOCK.getValue()));

        if (added) return UpdateOperation.LINK_UPDATED;
        return UpdateOperation.LINK_REMOVED;
    }

    protected UpdateOperation getUpdateOperation(int srcPortState) {
        boolean portUp = ((srcPortState & OFPortState.OFPPS_STP_MASK.getValue()) != OFPortState.OFPPS_STP_BLOCK.getValue());

        if (portUp)
            return UpdateOperation.PORT_UP;
        else
            return UpdateOperation.PORT_DOWN;
    }

    //************************************
    // Internal Methods - Link Operations Related
    //************************************

    /**
     * This method is used to specifically ignore/consider specific links.
     */
    protected boolean isLinkAllowed(long src, short srcPort,
                                    long dst, short dstPort) {
        // tunnel manager doesn't exist.
        if (tunnelManager == null) return true;

        Short srcTunnelPort = tunnelManager.getTunnelPortNumber(src);
        Short dstTunnelPort = tunnelManager.getTunnelPortNumber(dst);

        boolean srcTunnelFlag = (srcTunnelPort != null &&
                srcTunnelPort.shortValue() == srcPort);
        boolean dstTunnelFlag = (dstTunnelPort != null &&
                dstTunnelPort.shortValue() == dstPort);

        if (srcTunnelFlag && dstTunnelFlag) {
            // Tunnel links are not allowed to be handled. However updates are
            // being sent for tunnel liveness detection
            updates.add(new LDUpdate(src, srcPort, dst, dstPort,
                                     ILinkDiscovery.LinkType.TUNNEL,
                                     UpdateOperation.LINK_UPDATED));
            return false;
        } else if (srcTunnelFlag || dstTunnelFlag) {
            // one of them is tunnel, so there's something wrong
            // in the network.
            log.warn("Detecting link between a tunnel and a non-tunnel port. {}",
                     new Link(src, srcPort, dst, dstPort));
            return false;
        }

        return true;
    }

    private boolean addLink(Link lt, LinkInfo newInfo) {
        NodePortTuple srcNpt, dstNpt;

        srcNpt = new NodePortTuple(lt.getSrc(), lt.getSrcPort());
        dstNpt = new NodePortTuple(lt.getDst(), lt.getDstPort());

        // index it by switch source
        if (!switchLinks.containsKey(lt.getSrc()))
            switchLinks.put(lt.getSrc(),
                            new HashSet<Link>());
        switchLinks.get(lt.getSrc()).add(lt);

        // index it by switch dest
        if (!switchLinks.containsKey(lt.getDst()))
            switchLinks.put(lt.getDst(),
                            new HashSet<Link>());
        switchLinks.get(lt.getDst()).add(lt);

        // index both ends by switch:port
        if (!portLinks.containsKey(srcNpt))
            portLinks.put(srcNpt,
                          new HashSet<Link>());
        portLinks.get(srcNpt).add(lt);

        if (!portLinks.containsKey(dstNpt))
            portLinks.put(dstNpt,
                          new HashSet<Link>());
        portLinks.get(dstNpt).add(lt);

        return true;
    }

    protected boolean updateLink(Link lt, LinkInfo oldInfo, LinkInfo newInfo) {
        boolean linkChanged = false;
        // Since the link info is already there, we need to
        // update the right fields.
        if (newInfo.getUnicastValidTime() == null) {
            // This is due to a multicast LLDP, so copy the old unicast
            // value.
            if (oldInfo.getUnicastValidTime() != null) {
                newInfo.setUnicastValidTime(oldInfo.getUnicastValidTime());
            }
        } else if (newInfo.getMulticastValidTime() == null) {
            // This is due to a unicast LLDP, so copy the old multicast
            // value.
            if (oldInfo.getMulticastValidTime() != null) {
                newInfo.setMulticastValidTime(oldInfo.getMulticastValidTime());
            }
        }

        Long oldTime = oldInfo.getUnicastValidTime();
        Long newTime = newInfo.getUnicastValidTime();
        // the link has changed its state between openflow and
        // non-openflow
        // if the unicastValidTimes are null or not null
        if (oldTime != null & newTime == null) {
            linkChanged = true;
        } else if (oldTime == null & newTime != null) {
            linkChanged = true;
        }

        return linkChanged;
    }

    @LogMessageDocs({
        @LogMessageDoc(message="Inter-switch link detected:",
                explanation="Detected a new link between two openflow switches," +
                            "use show link to find current status"),
        @LogMessageDoc(message="Inter-switch link updated:",
                explanation="Detected a link change between two openflow switches, " +
                            "use show link to find current status")
    })
    protected boolean addOrUpdateLink(Link lt, LinkInfo newInfo) {

        boolean linkChanged = false;

        lock.writeLock().lock();
        try {
            // put the new info. if an old info exists, it will be returned.
            LinkInfo oldInfo = links.put(lt, newInfo);
            if (oldInfo != null
                    && oldInfo.getFirstSeenTime() < newInfo.getFirstSeenTime())
                newInfo.setFirstSeenTime(oldInfo.getFirstSeenTime());

            if (log.isTraceEnabled()) {
                log.trace("addOrUpdateLink: {} {}",
                          lt,
                          (newInfo.getMulticastValidTime() != null) ? "multicast"
                                                                      : "unicast");
            }

            UpdateOperation updateOperation = null;
            linkChanged = false;

            if (oldInfo == null) {
                addLink(lt, newInfo);
                updateOperation = UpdateOperation.LINK_UPDATED;
                linkChanged = true;

                // Log direct links only. Multi-hop links may be numerous
                // Add all to event history
                LinkType linkType = getLinkType(lt, newInfo);
                if (linkType == ILinkDiscovery.LinkType.DIRECT_LINK) {
                    log.trace("Inter-switch link detected: {}", lt);
                    evDirectLink.updateEventNoFlush(new DirectLinkEvent(lt.getSrc(),
                         lt.getSrcPort(), lt.getDst(), lt.getDstPort(), "direct-link-added::rcvd LLDP"));
                }
                notifier.postNotification("Link added: " + lt.toString());
            } else {
                linkChanged = updateLink(lt, oldInfo, newInfo);
                if (linkChanged) {
                    updateOperation = UpdateOperation.LINK_UPDATED;
                    LinkType linkType = getLinkType(lt, newInfo);
                    if (linkType == ILinkDiscovery.LinkType.DIRECT_LINK) {
                        log.trace("Inter-switch link updated: {}", lt);
                        evDirectLink.updateEventNoFlush(new DirectLinkEvent(lt.getSrc(),
                            lt.getSrcPort(), lt.getDst(), lt.getDstPort(),
                            "link-port-state-updated::rcvd LLDP"));
                    }
                    notifier.postNotification("Link updated: " + lt.toString());
                }
            }

            if (linkChanged) {
                // find out if the link was added or removed here.
                updates.add(new LDUpdate(lt.getSrc(), lt.getSrcPort(),
                                         lt.getDst(), lt.getDstPort(),
                                         getLinkType(lt, newInfo),
                                         updateOperation));
            }
        } finally {
            lock.writeLock().unlock();
        }

        return linkChanged;
    }

    /**
     * Delete a link
     *
     * @param link
     *            - link to be deleted.
     * @param reason
     *            - reason why the link is deleted.
     */
    protected void deleteLink(Link link, String reason) {
        if (link == null)
            return;
        List<Link> linkList = new ArrayList<Link>();
        linkList.add(link);
        deleteLinks(linkList, reason);
    }
    /**
     * Removes links from memory and storage.
     *
     * @param links
     *            The List of @LinkTuple to delete.
     */
    protected void deleteLinks(List<Link> links, String reason) {
        deleteLinks(links, reason, null);
    }
    
    /**
     * Syncs the the link tuple and link info map with the slave nodes. The
     * update is performed only if the role is master.
     *
     * @param lt
     * @param linkInfo
     */
    protected void writeLinkState(Link lt, LinkInfo linkInfo) {
        // Do not write links if the role is not null & not master.
        // null value may indicate this is a non-ha configuration.
        if (role != null && role != Role.MASTER) return;

        while (true) {
            try {
                Versioned<LinkInfo> v = linkStoreClient.get(lt);
                v.setValue(linkInfo);
                linkStoreClient.put(lt, v);
                cntSyncStorePuts.updateCounterNoFlush();
                break;
            } catch (ObsoleteVersionException e) {
                log.warn("Failed to write link info {} "
                                 + "[Error Code/Message: {}]", linkInfo,
                         new Object[] { e.getErrorType(), e.getMessage() });
                cntSyncStorePutsFailedObsoleteVersion.updateCounterNoFlush();
            } catch (SyncException e) {
                log.warn("Failed to store link info {} "
                        + "[Error Code/Message: {}]", linkInfo,
                        new Object[] { e.getErrorType(), e.getMessage() });
                cntSyncStorePutsFailed.updateCounterNoFlush();
                break;
            }
        }
    }
    
    /**
     * Removes the link tuple key from the sync store at the slave node. The
     * update is performed only if the role is master.
     */
    protected void removeLinkState(Link lt) {
        // Do not write links if the role is not null & not master.
        // null value may indicate this is a non-ha configuration.
        if (role != null && role != Role.MASTER) return;

        while (true) {
            try {
                linkStoreClient.delete(lt);
                cntSyncStoreDeletes.updateCounterNoFlush();
                break;
            } catch (ObsoleteVersionException e) {
                log.warn("Failed to delete link {} "
                                 + "[Error Code/Message: {}]", lt,
                         new Object[] { e.getErrorType(), e.getMessage() });
                cntSyncStoreDeletesFailedObsoleteVersion.updateCounterNoFlush();
            } catch (SyncException e) {
                log.warn("Failed to remove link info {} "
                                 + "[Error Code/Message: {}]", lt,
                         new Object[] { e.getErrorType(), e.getMessage() });
                cntSyncStoreDeletesFailed.updateCounterNoFlush();
                break;
            }
        }
    }

    /**
     * Removes links from memory and storage.
     *
     * @param links
     *            The List of @LinkTuple to delete.
     */
    @LogMessageDoc(message="Inter-switch link removed:",
            explanation="A previously detected link between two openflow switches no longer exists, " +
                        "use show link to find current status")
    protected void deleteLinks(List<Link> links, String reason,
                               List<LDUpdate> updateList) {

        NodePortTuple srcNpt, dstNpt;
        List<LDUpdate> linkUpdateList = new ArrayList<LDUpdate>();
        lock.writeLock().lock();
        try {
            for (Link lt : links) {
                srcNpt = new NodePortTuple(lt.getSrc(), lt.getSrcPort());
                dstNpt = new NodePortTuple(lt.getDst(), lt.getDstPort());

                if (switchLinks.containsKey(lt.getSrc())) {
                    switchLinks.get(lt.getSrc()).remove(lt);
                    if (switchLinks.get(lt.getSrc()).isEmpty())
                        this.switchLinks.remove(lt.getSrc());
                }
                if (this.switchLinks.containsKey(lt.getDst())) {
                    switchLinks.get(lt.getDst()).remove(lt);
                    if (this.switchLinks.get(lt.getDst()).isEmpty())
                        this.switchLinks.remove(lt.getDst());
                }

                if (this.portLinks.get(srcNpt) != null) {
                    this.portLinks.get(srcNpt).remove(lt);
                    if (this.portLinks.get(srcNpt).isEmpty())
                                                             this.portLinks.remove(srcNpt);
                }
                if (this.portLinks.get(dstNpt) != null) {
                    this.portLinks.get(dstNpt).remove(lt);
                    if (this.portLinks.get(dstNpt).isEmpty())
                                                             this.portLinks.remove(dstNpt);
                }

                LinkInfo info = this.links.remove(lt);
                LinkType linkType = getLinkType(lt, info);
                linkUpdateList.add(new LDUpdate(lt.getSrc(),
                                                lt.getSrcPort(),
                                                lt.getDst(),
                                                lt.getDstPort(),
                                                linkType,
                                                UpdateOperation.LINK_REMOVED));

                // FIXME: link type shows up as invalid now -- thus not checking if
                // link type is a direct link
                evDirectLink.updateEventWithFlush(new DirectLinkEvent(lt.getSrc(),
                      lt.getSrcPort(), lt.getDst(), lt.getDstPort(),
                      "link-deleted::" + reason));
                // remove link from storage.
                removeLinkState(lt);

                // TODO Whenever link is removed, it has to checked if
                // the switchports must be added to quarantine.

                if (linkType == ILinkDiscovery.LinkType.DIRECT_LINK) {
                    log.trace("Inter-switch link removed: {}", lt);
                    notifier.postNotification("Inter-switch link removed: " +
                                              lt.toString());
                } else if (log.isTraceEnabled()) {
                    log.trace("Deleted link {}", lt);
                }
            }
        } finally {
            if (updateList != null) linkUpdateList.addAll(updateList);
            updates.addAll(linkUpdateList);
            lock.writeLock().unlock();
        }
    }

    /**
     * Delete links incident on a given switch port.
     *
     * @param npt
     * @param reason
     */
    protected void deleteLinksOnPort(NodePortTuple npt, String reason) {
        List<Link> eraseList = new ArrayList<Link>();
        if (this.portLinks.containsKey(npt)) {
            if (log.isTraceEnabled()) {
                log.trace("handlePortStatus: Switch {} port #{} "
                                  + "removing links {}",
                          new Object[] {
                                        HexString.toHexString(npt.getNodeId()),
                                        npt.getPortId(),
                                        this.portLinks.get(npt) });
            }
            eraseList.addAll(this.portLinks.get(npt));
            deleteLinks(eraseList, reason);
        }
    }

    /**
     * Iterates through the list of links and deletes if the last discovery
     * message reception time exceeds timeout values.
     */
    protected void timeoutLinks() {
        List<Link> eraseList = new ArrayList<Link>();
        Long curTime = System.currentTimeMillis();
        boolean linkChanged = false;

        // reentrant required here because deleteLink also write locks
        lock.writeLock().lock();
        try {
            Iterator<Entry<Link, LinkInfo>> it = this.links.entrySet()
                                                           .iterator();
            while (it.hasNext()) {
                Entry<Link, LinkInfo> entry = it.next();
                Link lt = entry.getKey();
                LinkInfo info = entry.getValue();

                // Timeout the unicast and multicast LLDP valid times
                // independently.
                if ((info.getUnicastValidTime() != null)
                    && (info.getUnicastValidTime()
                        + (this.LINK_TIMEOUT * 1000L) < curTime)) {
                    info.setUnicastValidTime(null);
                    linkChanged = true;
                }
                if ((info.getMulticastValidTime() != null)
                    && (info.getMulticastValidTime()
                        + (this.LINK_TIMEOUT * 1000L) < curTime)) {
                    info.setMulticastValidTime(null);
                    linkChanged = true;
                }
                // Add to the erase list only if the unicast
                // time is null.
                if (info.getUnicastValidTime() == null
                    && info.getMulticastValidTime() == null) {
                    eraseList.add(entry.getKey());
                } else if (linkChanged) {
                    updates.add(new LDUpdate(lt.getSrc(), lt.getSrcPort(),
                                             lt.getDst(), lt.getDstPort(),
                                             getLinkType(lt, info),
                                             UpdateOperation.LINK_UPDATED));
                }
            }

            // if any link was deleted or any link was changed.
            if ((eraseList.size() > 0) || linkChanged) {
                deleteLinks(eraseList, "LLDP timeout");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    //******************
    // Internal Helper Methods
    //******************
    @LogMessageDoc(level="WARN",
            message="Could not get list of interfaces of local machine to " +
                     "encode in TLV: {detail-msg}",
            explanation="Outgoing LLDP packets encode a unique hash to " +
                     "identify the local machine. The list of network " +
                     "interfaces is used as input and the controller failed " +
                     "to query this list",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    protected void setControllerTLV() {
        // Setting the controllerTLVValue based on current nano time,
        // controller's IP address, and the network interface object hash
        // the corresponding IP address.

        final int prime = 7867;

        byte[] controllerTLVValue = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 }; // 8
                                                                           // byte
                                                                           // value.
        ByteBuffer bb = ByteBuffer.allocate(10);

        long result = System.nanoTime();
        try{
            // Use some data specific to the machine this controller is
            // running on. In this case: the list of network interfaces
            Enumeration<NetworkInterface> ifaces =
                    NetworkInterface.getNetworkInterfaces();
            if (ifaces != null) {
                result = result * prime + ifaces.hashCode();
            }
        } catch (SocketException e) {
            log.warn("Could not get list of interfaces of local machine to " +
                     "encode in TLV: {}", e.toString());
        }
        // set the first 4 bits to 0.
        result = result & (0x0fffffffffffffffL);

        bb.putLong(result);

        bb.rewind();
        bb.get(controllerTLVValue, 0, 8);

        this.controllerTLV = new LLDPTLV().setType((byte) 0x0c)
                                          .setLength((short) controllerTLVValue.length)
                                          .setValue(controllerTLVValue);
    }

    /**
     * Getter and setters for sync manager.
     */

    public ISyncService getSyncManager() {
        return syncManager;
    }

    public void setSyncManager(ISyncService syncManager) {
        this.syncManager = syncManager;
    }

    /**
     * Getter and setter methods for linkStoreClient.
     */
    public IStoreClient<Link, LinkInfo> getLinkStoreClient() {
        return linkStoreClient;
    }

    public void
            setLinkStoreClient(IStoreClient<Link, LinkInfo> linkStoreClient) {
        this.linkStoreClient = linkStoreClient;
    }

    //******************
    // IOFSwitchListener
    //******************
    private void handlePortDown(long switchId, short portNumber) {
            NodePortTuple npt = new NodePortTuple(switchId, portNumber);
            deleteLinksOnPort(npt, "Port Status Changed");
            LDUpdate update = new LDUpdate(switchId, portNumber,
                    UpdateOperation.PORT_DOWN);
            updates.add(update);
    }
    /**
     * We don't react the port changed notifications here. we listen for
     * OFPortStatus messages directly. Might consider using this notifier
     * instead
     */
    @Override
    public void switchPortChanged(long switchId,
            ImmutablePort port,
            IOFSwitch.PortChangeType type) {

        switch (type) {
        case UP:
            processNewPort(switchId, port.getPortNumber());
            break;
        case DELETE: case DOWN:
            handlePortDown(switchId, port.getPortNumber());
            break;
        case OTHER_UPDATE: case ADD:
            // This is something other than port add or delete.
            // Topology does not worry about this.
            // If for some reason the port features change, which
            // we may have to react.
            break;
        }
    }

    @Override
    public void switchAdded(long switchId) {
        // no-op
        // We don't do anything at switch added, but we do only when the
        // switch is activated.
    }

    @Override
    public void switchRemoved(long sw) {
        List<Link> eraseList = new ArrayList<Link>();
        lock.writeLock().lock();
        try {
            if (switchLinks.containsKey(sw)) {
                if (log.isTraceEnabled()) {
                    log.trace("Handle switchRemoved. Switch {}; removing links {}",
                              HexString.toHexString(sw), switchLinks.get(sw));
                }

                List<LDUpdate> updateList = new ArrayList<LDUpdate>();
                updateList.add(new LDUpdate(sw, null,
                                            UpdateOperation.SWITCH_REMOVED));
                // add all tuples with an endpoint on this switch to erase list
                eraseList.addAll(switchLinks.get(sw));

                // Sending the updateList, will ensure the updates in this
                // list will be added at the end of all the link updates.
                // Thus, it is not necessary to explicitly add these updates
                // to the queue.
                deleteLinks(eraseList, "Switch Removed", updateList);
            } else {
                // Switch does not have any links.
                updates.add(new LDUpdate(sw, null,
                                         UpdateOperation.SWITCH_REMOVED));
            }
        } finally {
            lock.writeLock().unlock();
        }

    }

    @Override
    public void switchActivated(long switchId) {
        IOFSwitch sw = floodlightProvider.getSwitch(switchId);
        if (sw.getEnabledPortNumbers() != null) {
            for (Short p : sw.getEnabledPortNumbers()) {
                processNewPort(sw.getId(), p);
            }
        }
        LDUpdate update = new LDUpdate(sw.getId(), null,
                                       UpdateOperation.SWITCH_UPDATED);
        updates.add(update);
    }

    @Override
    public void switchChanged(long switchId) {
        // no-op
    }

    //******************************
    // Internal methods - Config Related
    //******************************

    private class LinkDiscoveryWatchHook implements WatchHook {
        @Override
        public void watch(Context context) {
            readTopologyConfig();
        }        
    }
    
    @LogMessageDoc(level = "ERROR",
                   message = "Failed to read topology configuration",
                   explanation = "An error occurred while reading the configuration",
                   recommendation = LogMessageDoc.REPORT_CONTROLLER_BUG)
    protected void readTopologyConfig() {
        try {
            Query q = Query.parse("/topology/config/auto-port-fast");
            Treespace t = dbService.getControllerTreespace();
            DataNodeSet dns = t.queryData(q, AuthContext.SYSTEM);
            DataNode d = dns.getSingleDataNode();
            autoPortFastFeature = d.getBoolean(true);
        } catch (BigDBException e) {
            log.error("Failed to read topology configuration", e);
        }
    }

    //***************
    // IFloodlightModule
    //***************

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(ILinkDiscoveryService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m =
                new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        // We are the class that implements the service
        m.put(ILinkDiscoveryService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IBigDBService.class);
        l.add(IThreadPoolService.class);
        l.add(ISyncService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        dbService = context.getServiceImpl(IBigDBService.class);
        threadPool = context.getServiceImpl(IThreadPoolService.class);
        debugCounters = context.getServiceImpl(IDebugCounterService.class);
        debugEvents = context.getServiceImpl(IDebugEventService.class);
        syncManager = context.getServiceImpl(ISyncService.class);
        tunnelManager = context.getServiceImpl(ITunnelService.class);

        // read our config options
        Map<String, String> configOptions = context.getConfigParams(this);
        try {
            String histSize = configOptions.get("eventhistorysize");
            if (histSize != null) {
                eventHistorySize = Short.parseShort(histSize);
            }
        } catch (NumberFormatException e) {
            log.warn("Error event history size, using default of {} seconds", eventHistorySize);
        }
        log.debug("Event history size set to {}", eventHistorySize);

        // We create this here because there is no ordering guarantee
        this.linkDiscoveryAware = new ArrayList<ILinkDiscoveryListener>();
        this.lock = new ReentrantReadWriteLock();
        this.updates = new LinkedBlockingQueue<LDUpdate>();
        this.links = new HashMap<Link, LinkInfo>();
        this.portLinks = new HashMap<NodePortTuple, Set<Link>>();
        this.suppressLinkDiscovery = Collections.synchronizedSet(new HashSet<NodePortTuple>());
        this.switchLinks = new HashMap<Long, Set<Link>>();
        this.quarantineQueue = new LinkedBlockingQueue<NodePortTuple>();
        this.maintenanceQueue = new LinkedBlockingQueue<NodePortTuple>();

        this.ignoreMACSet = Collections.newSetFromMap(
                                new ConcurrentHashMap<MACRange,Boolean>());
        this.haListener = new HAListenerDelegate();
        registerLinkDiscoveryDebugCounters();
        registerLinkDiscoveryDebugEvents();
    }

    @Override
    @LogMessageDoc(level = "ERROR",
                   message = "Exception in LLDP send timer.",
                   explanation = "An unknown error occured while sending LLDP "
                               + "messages to switches.",
                   recommendation = LogMessageDoc.CHECK_SWITCH)
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        // Initialize configuration
        try {
            Treespace t = dbService.getControllerTreespace();
            if (t != null) {
                WatchHook watchHook = new LinkDiscoveryWatchHook();
                LocationPathExpression path = 
                        LocationPathExpression.parse("/topology/config");
                t.getHookRegistry().registerWatchHook(path, false, watchHook);
                readTopologyConfig();
            }
        } catch (BigDBException e) {
            throw new FloodlightModuleException(e);
        }

        // Initialize role to floodlight provider role.
        this.role = floodlightProvider.getRole();

        ScheduledExecutorService ses = threadPool.getScheduledExecutor();
        
        // To be started by the first switch connection
        discoveryTask = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                try {
                    discoverLinks();
                } catch (Exception e) {
                    log.error("Exception in LLDP send timer.", e);
                } finally {
                    if (!shuttingDown) {
                        // null role implies HA mode is not enabled.
                        if (role == null || role == Role.MASTER) {
                            log.trace("Rescheduling discovery task as role = {}",
                                      role);
                            discoveryTask.reschedule(DISCOVERY_TASK_INTERVAL,
                                                     TimeUnit.SECONDS);
                        } else {
                            log.trace("Stopped LLDP rescheduling due to role = {}.",
                                      role);
                        }
                    }
                }
            }
        });

        // null role implies HA mode is not enabled.
        if (role == null || role == Role.MASTER) {
            log.trace("Setup: Rescheduling discovery task. role = {}", role);
            discoveryTask.reschedule(DISCOVERY_TASK_INTERVAL,
                                     TimeUnit.SECONDS);
        } else {
            log.trace("Setup: Not scheduling LLDP as role = {}.", role);
        }

        // Setup the BDDP task. It is invoked whenever switch port tuples
        // are added to the quarantine list.
        bddpTask = new SingletonTask(ses, new QuarantineWorker());
        bddpTask.reschedule(BDDP_TASK_INTERVAL, TimeUnit.MILLISECONDS);

        updatesThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        doUpdatesThread();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "Topology Updates");
        updatesThread.start();

        // Register for the OpenFlow messages we want to receive
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.PORT_STATUS, this);
        // Register for switch updates
        floodlightProvider.addOFSwitchListener(this);
        floodlightProvider.addHAListener(this.haListener);
        floodlightProvider.addInfoProvider("summary", this);
        setControllerTLV();
        
        try {
            syncManager.registerStore(LINK_DISCOVERY_STORE_NAME, Scope.LOCAL);
            try {
                IStoreClient<Link, LinkInfo> storeClient;
                storeClient = syncManager.getStoreClient(LINK_DISCOVERY_STORE_NAME,
                                                         Link.class,
                                                         LinkInfo.class);
                if (storeClient != null) {
                    linkStoreClient = storeClient;
                    linkStoreClient.addStoreListener(this);
                }
            } catch (UnknownStoreException e) {
                log.warn("Invalid Store Name {} for Link State Error Code/Msg {}",
                         LINK_DISCOVERY_STORE_NAME,
                         new Object[] { e.getErrorType(), e.getMessage() });
            }
        } catch (SyncException e) {
            log.warn("Failed to register Store {} Error Code/Message {}",
                        LINK_DISCOVERY_STORE_NAME,
                        new Object[] { e.getErrorType(), e.getMessage() });
        }
    }

    // ****************************************************
    // Link Discovery DebugCounters and DebugEvents
    // ****************************************************

    private void registerLinkDiscoveryDebugCounters() throws FloodlightModuleException {
        if (debugCounters == null) {
            log.error("Debug Counter Service not found.");
            debugCounters = new NullDebugCounter();
        }
        try {
            ctrIncoming = debugCounters.registerCounter(PACKAGE, "incoming",
                "All incoming packets seen by this module", CounterType.ALWAYS_COUNT);
            ctrLldpEol  = debugCounters.registerCounter(PACKAGE, "lldp-eol",
                "End of Life for LLDP packets", CounterType.COUNT_ON_DEMAND);
            ctrLinkLocalDrops = debugCounters.registerCounter(PACKAGE, "linklocal-drops",
                "All link local packets dropped by this module",
                CounterType.ALWAYS_COUNT);
            ctrIgnoreSrcMacDrops = debugCounters.registerCounter(PACKAGE, "ignore-srcmac-drops",
                "All packets whose srcmac is configured to be dropped by this module",
                CounterType.ALWAYS_COUNT);
            ctrQuarantineDrops = debugCounters.registerCounter(PACKAGE, "quarantine-drops",
                "All packets arriving on quarantined ports dropped by this module",
                CounterType.ALWAYS_COUNT, IDebugCounterService.CTR_MDATA_WARN);
            cntSyncStorePuts = debugCounters.registerCounter(PACKAGE, "sync-puts",
                "Number of syncStore put() calls",
                CounterType.COUNT_ON_DEMAND);
            // counters related to failed syncStore put() calls
            cntSyncStorePutsFailed = debugCounters.registerCounter(PACKAGE,
                "sync-puts/failed",
                "Number of syncStore put() calls that failed",
                CounterType.ALWAYS_COUNT);
            cntSyncStorePutsFailedObsoleteVersion = 
                    debugCounters. registerCounter(PACKAGE, 
                    "sync-puts/failed/obsolete-version-exception",
                    "Number of syncStore put() calls that failed due to " +
                            "an obsolete version exception",
                            CounterType.ALWAYS_COUNT);
            // counters related to successful syncStore delete() calls
            cntSyncStoreDeletes = 
                    debugCounters.registerCounter(PACKAGE,
                                                  "sync-deletes",
                                                  "Number of syncStore delete() calls",
                                                  CounterType.COUNT_ON_DEMAND);
            // counters related to failed syncStore delete() calls
            cntSyncStoreDeletesFailed = 
                    debugCounters.registerCounter(PACKAGE,
                                                  "sync-deletes/failed",
                                                  "Number of syncStore delete() calls that failed",
                                                  CounterType.ALWAYS_COUNT);
            cntSyncStoreDeletesFailedObsoleteVersion = debugCounters
                    .registerCounter(PACKAGE,
                                     "sync-deletes/failed/obsolete-version-exception",
                                     "Number of syncStore delete() calls that failed due to " +
                                     "an obsolete version exception",
                                     CounterType.ALWAYS_COUNT);
            // counters related to syncStore keysModified() notifications
            cntSyncStoreKeysModified = 
                    debugCounters.registerCounter(PACKAGE,
                                                  "sync-keys-modified",
                                                  "Number of keysModified() calls",
                                                  CounterType.COUNT_ON_DEMAND);
            cntSyncStoreKeysModifiedNullKeys = debugCounters
                    .registerCounter(PACKAGE,
                                     "sync-keys-modified/with-null-keys",
                                     "Number of keysModified() calls with null keys",
                                     CounterType.ALWAYS_COUNT);
        } catch (CounterException e) {
            throw new FloodlightModuleException(e.getMessage());
        }
    }

    private void registerLinkDiscoveryDebugEvents() throws FloodlightModuleException {
        if (debugEvents == null) {
            log.error("Debug Event Service not found.");
            debugEvents = new NullDebugEvent();
        }

        try {
            evDirectLink = debugEvents.registerEvent(
                               PACKAGE, "linkevent",
                               "Direct OpenFlow links discovered or timed-out",
                               EventType.ALWAYS_LOG, DirectLinkEvent.class, 100);
        } catch (MaxEventsRegistered e) {
            throw new FloodlightModuleException("max events registered", e);
        }

    }

    public class DirectLinkEvent {
        @EventColumn(name = "srcSw", description = EventFieldType.DPID)
        long srcDpid;

        @EventColumn(name = "srcPort", description = EventFieldType.PRIMITIVE)
        short srcPort;

        @EventColumn(name = "dstSw", description = EventFieldType.DPID)
        long dstDpid;

        @EventColumn(name = "dstPort", description = EventFieldType.PRIMITIVE)
        short dstPort;

        @EventColumn(name = "reason", description = EventFieldType.STRING)
        String reason;

        public DirectLinkEvent(long srcDpid, short srcPort, long dstDpid,
                               short dstPort, String reason) {
            this.srcDpid = srcDpid;
            this.srcPort = srcPort;
            this.dstDpid = dstDpid;
            this.dstPort = dstPort;
            this.reason = reason;
        }
    }


    //*********************
    //  IInfoProvider
    //*********************

    @Override
    public Map<String, Object> getInfo(String type) {
        if (!"summary".equals(type)) return null;

        Map<String, Object> info = new HashMap<String, Object>();

        int numDirectLinks = 0;
        for (Set<Link> links : switchLinks.values()) {
            for (Link link : links) {
                LinkInfo linkInfo = this.getLinkInfo(link);
                if (linkInfo != null &&
                    linkInfo.getLinkType() == LinkType.DIRECT_LINK) {
                    numDirectLinks++;
                }
            }
        }
        info.put("# inter-switch links", numDirectLinks / 2);
        info.put("# quarantine ports", quarantineQueue.size());
        return info;
    }

    //***************
    // IHAListener
    //***************

    private class HAListenerDelegate implements IHAListener {
        @Override
        public void  transitionToMaster() {
            if (log.isTraceEnabled()) {
                log.trace("Sending LLDPs "
                        + "to HA change from SLAVE->MASTER");
            }
            LinkDiscoveryManager.this.role = Role.MASTER;
            log.debug("Role Change to Master: Rescheduling discovery task.");
            discoveryTask.reschedule(1, TimeUnit.MICROSECONDS);
        }

        @Override
        public void controllerNodeIPsChanged(Map<String, String> curControllerNodeIPs,
                                             Map<String, String> addedControllerNodeIPs,
                                             Map<String, String> removedControllerNodeIPs) {
            // ignore
        }

        @Override
        public String getName() {
            return LinkDiscoveryManager.this.getName();
        }

        @Override
        public boolean isCallbackOrderingPrereq(HAListenerTypeMarker type,
                                                String name) {
            return ("topology".equals(name));
        }

        @Override
        public boolean isCallbackOrderingPostreq(HAListenerTypeMarker type,
                                                 String name) {
            return "tunnelmanager".equals(name);
        }
    }

    //*********************
    // IStoreListener<Link>
    //*********************
    
    /**
     * This method updates the link discovery data structures based on the keys
     * that were modified. The keys modified action is performed by both the
     * master and slave nodes.
     */
    @Override
    public void keysModified(Iterator<Link> keys, 
                             IStoreListener.UpdateType type) {

        // Ignore updates from local.
        if (type != IStoreListener.UpdateType.REMOTE)
            return;

        // check for null.
        if (keys == null) {
            cntSyncStoreKeysModifiedNullKeys.updateCounterWithFlush();
            return;
        }
        cntSyncStoreKeysModified.updateCounterWithFlush();

        while (keys.hasNext()) {
            Link link = keys.next();
            try {
                Versioned<LinkInfo> v = linkStoreClient.get(link);
                LinkInfo newInfo = v.getValue();

                // if newInfo is null, then the key is deleted.
                if (newInfo == null) {
                    deleteLink(link, "Removed by master");
                    if (log.isTraceEnabled()) {
                        log.trace("Deleting link {}", link);
                    }
                } else {
                    addOrUpdateLink(link, newInfo);
                    if (log.isTraceEnabled()) {
                        log.trace("Adding/Updating link {}", newInfo);
                    }
                }
            } catch (SyncException e) {
                log.warn("Failed to get value for modified key {} "
                                 + "ErrorCode/Msg {}", link,
                         new Object[] { e.getErrorType(), e.getMessage() });
            }
        }
    }
}
