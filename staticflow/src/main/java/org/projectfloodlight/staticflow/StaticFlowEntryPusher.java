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

package org.projectfloodlight.staticflow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionEnqueue;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.protocol.action.OFActionNetworkLayerSource;
import org.openflow.protocol.action.OFActionNetworkTypeOfService;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionStripVirtualLan;
import org.openflow.protocol.action.OFActionTransportLayerDestination;
import org.openflow.protocol.action.OFActionTransportLayerSource;
import org.openflow.protocol.action.OFActionVirtualLanIdentifier;
import org.openflow.protocol.action.OFActionVirtualLanPriorityCodePoint;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.projectfloodlight.core.FloodlightContext;
import org.projectfloodlight.core.HAListenerTypeMarker;
import org.projectfloodlight.core.IFloodlightProviderService;
import org.projectfloodlight.core.IHAListener;
import org.projectfloodlight.core.IOFMessageListener;
import org.projectfloodlight.core.IOFSwitch;
import org.projectfloodlight.core.IOFSwitchListener;
import org.projectfloodlight.core.ImmutablePort;
import org.projectfloodlight.core.annotations.LogMessageCategory;
import org.projectfloodlight.core.annotations.LogMessageDoc;
import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.core.module.FloodlightModuleException;
import org.projectfloodlight.core.module.IFloodlightModule;
import org.projectfloodlight.core.module.IFloodlightService;
import org.projectfloodlight.core.util.AppCookie;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.IBigDBService;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeNotFoundException;
import org.projectfloodlight.db.data.DataNodeSet;
import org.projectfloodlight.db.data.MutationListener;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.service.Treespace;
import org.projectfloodlight.packet.IPv4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@LogMessageCategory("Static Flow Pusher")
/**
 * This module is responsible for maintaining a set of static flows on
 * switches. This is just a big 'ol dumb list of flows and something external
 * is responsible for ensuring they make sense for the network.
 */
public class StaticFlowEntryPusher
    implements IOFSwitchListener, IFloodlightModule, IStaticFlowEntryPusherService,
        IOFMessageListener, MutationListener {
    protected static final Logger log = 
            LoggerFactory.getLogger(StaticFlowEntryPusher.class);
    public static final String StaticFlowName = "staticflowentry";

    public static final int STATIC_FLOW_APP_ID = 10;
    static {
        AppCookie.registerApp(STATIC_FLOW_APP_ID, StaticFlowName);
    }
    
    protected IFloodlightProviderService floodlightProvider;
    protected IBigDBService bigDBService;
    protected Treespace treespace;

    private IHAListener haListener;

    // Map<DPID, Map<Name, FlowMod>>; FlowMod can be null to indicate non-active
    protected Map<String, Map<String, OFFlowMod>> entriesFromStorage;

    // defaults
    private static boolean DEFAULT_ACTIVE = false;
    private static int DEFAULT_COOKIE = 0;
    private static int DEFAULT_PRIORITY = 32768;
    private static short DEFAULT_IDLE_TIMEOUT = 0;
    private static short DEFAULT_HARD_TIMEOUT = 0;
    private static short DEFAULT_FLAGS = 0;

    // Class to sort FlowMod's by priority, from lowest to highest
    class FlowModSorter implements Comparator<String> {
        private final String dpid;
        public FlowModSorter(String dpid) {
            this.dpid = dpid;
        }
        @Override
        public int compare(String o1, String o2) {
            OFFlowMod f1 = entriesFromStorage.get(dpid).get(o1);
            OFFlowMod f2 = entriesFromStorage.get(dpid).get(o2);
            if (f1 == null || f2 == null) // sort active=false flows by key
                return o1.compareTo(o2);
            return U16.f(f1.getPriority()) - U16.f(f2.getPriority());
        }
    };

    /**
     * Reads from our entriesFromStorage for the specified switch and
     * sends the FlowMods down to the controller in <b>sorted</b> order.
     *
     * Sorted is important to maintain correctness of the switch:
     * if a packet would match both a lower and a higher priority
     * rule, then we want it to match the higher priority or nothing,
     * but never just the lower priority one.  Inserting from high to
     * low priority fixes this.
     *
     * TODO consider adding a "block all" flow mod and then removing it
     * while starting up.
     *
     * @param sw The switch to send entries to
     */
    protected void sendEntriesToSwitch(long switchId) {
        IOFSwitch sw = floodlightProvider.getSwitch(switchId);
        if (sw == null)
            return;
        String stringId = sw.getStringId();

        if ((entriesFromStorage != null) && (entriesFromStorage.containsKey(stringId))) {
            Map<String, OFFlowMod> entries = entriesFromStorage.get(stringId);
            List<String> sortedList = new ArrayList<String>(entries.keySet());
            Collections.sort( sortedList, new FlowModSorter(stringId));
            for (String entryName : sortedList) {
                OFFlowMod flowMod = entries.get(entryName);
                if (flowMod != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Pushing static entry {} for {}", stringId, entryName);
                    }
                    writeFlowModToSwitch(sw.getId(), flowMod);
                }
            }
        }
    }

    /**
     * Used only for bundle-local indexing
     *
     * @param map
     * @return
     */
    private static void initDefaultFlowMod(OFFlowMod fm) {
        fm.setIdleTimeout(DEFAULT_IDLE_TIMEOUT);   // infinite
        fm.setHardTimeout(DEFAULT_HARD_TIMEOUT);   // infinite
        fm.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        fm.setCommand(OFFlowMod.OFPFC_ADD);
        fm.setFlags(DEFAULT_FLAGS);
        fm.setOutPort(OFPort.OFPP_NONE.getValue());
        fm.setCookie(AppCookie.makeCookie(StaticFlowEntryPusher.STATIC_FLOW_APP_ID, DEFAULT_COOKIE));
        fm.setPriority(Short.MAX_VALUE);
        fm.setMatch(new OFMatch());
    }

    private void parseFlowEntryDataNode(String dpidString, String entryName, DataNode flowEntryDataNode,
            Map<String, Map<String, OFFlowMod>> entries) throws BigDBException {
        if (flowEntryDataNode.getChild("flow-mod").isNull()) {
            // if there is no flowMod we don't pay any attention to it until one exists
            return;
        }
        OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        initDefaultFlowMod(flowMod);
        DataNode dnFm = flowEntryDataNode.getChild("flow-mod");

        DataNode cookieDn = dnFm.getChild("cookie");
        flowMod.setCookie(AppCookie.makeCookie(STATIC_FLOW_APP_ID, (int) cookieDn.getLong(DEFAULT_COOKIE)));

        DataNode priority = dnFm.getChild("priority");
        flowMod.setPriority((short) priority.getLong(DEFAULT_PRIORITY));
        parseMatchFields(dnFm.getChild("match"), flowMod);
        parseActionList(dnFm.getChild("action"), flowMod);

        // Add the entry to our internal datastructures
        if (!entries.containsKey(dpidString)) {
            entries.put(dpidString, new HashMap<String, OFFlowMod>());
        }

        // TODO - refactor so we don't track inactive entries
        boolean active = flowEntryDataNode.getChild("active").getBoolean(DEFAULT_ACTIVE);
        Map<String, OFFlowMod> swEntries = entries.get(dpidString);
        if (active){
            swEntries.put(entryName, flowMod);
        } else {
            swEntries.put(entryName, null); // null means inactive
        }
    }

    private void parseFlowEntryListDataNode(String dpidString,
            Iterable<DataNode> flowEntryDataNodes,
            Map<String, Map<String, OFFlowMod>> entries)
            throws BigDBException {
        for (DataNode flowEntryDataNode : flowEntryDataNodes) {
            if (!flowEntryDataNode.hasChild("name")) {
                // TODO - handle this properly
                throw new BigDBException ("Name must be specified!");
            }
            String entryName = flowEntryDataNode.getChild("name").getString();
            parseFlowEntryDataNode(dpidString, entryName, flowEntryDataNode, entries);
        }
    }

    private void parseMatchFields(DataNode match, OFFlowMod fm) throws BigDBException {
        if (match == null) return;
        OFMatch fmMatch = fm.getMatch();
        fmMatch.setWildcards(OFMatch.OFPFW_ALL);
        if (match.hasChild(OFMatch.STR_IN_PORT)) {
            DataNode inPort = match.getChild(OFMatch.STR_IN_PORT);
            fmMatch.setInputPort((short)inPort.getLong());
            fmMatch.setWildcards(fmMatch.getWildcards() & ~OFMatch.OFPFW_IN_PORT);
        }
        if (match.hasChild(OFMatch.STR_DL_SRC)) {
            DataNode dlSrc = match.getChild(OFMatch.STR_DL_SRC);
            fmMatch.setDataLayerSource(dlSrc.getString());
            fmMatch.setWildcards(fmMatch.getWildcards() & ~OFMatch.OFPFW_DL_SRC);
        }
        if (match.hasChild(OFMatch.STR_DL_DST)) {
            DataNode dlDst = match.getChild(OFMatch.STR_DL_DST);
            fmMatch.setDataLayerDestination(dlDst.getString());
            fmMatch.setWildcards(fmMatch.getWildcards() & ~OFMatch.OFPFW_DL_DST);
        }
        if (match.hasChild(OFMatch.STR_DL_VLAN)) {
            DataNode dlVlan = match.getChild(OFMatch.STR_DL_VLAN);
            fmMatch.setDataLayerVirtualLan((short)dlVlan.getLong());
            fmMatch.setWildcards(fmMatch.getWildcards() & ~OFMatch.OFPFW_DL_VLAN);
        }
        if (match.hasChild(OFMatch.STR_DL_VLAN_PCP)) {
            DataNode dlVlanPcp = match.getChild(OFMatch.STR_DL_VLAN_PCP);
            fmMatch.setDataLayerVirtualLanPriorityCodePoint((byte)dlVlanPcp.getLong());
            fmMatch.setWildcards(fmMatch.getWildcards() & ~OFMatch.OFPFW_DL_VLAN_PCP);
        }
        if (match.hasChild(OFMatch.STR_DL_TYPE)) {
            DataNode dlType = match.getChild(OFMatch.STR_DL_TYPE);
            fmMatch.setDataLayerType((short)dlType.getLong());
            fmMatch.setWildcards(fmMatch.getWildcards() & ~OFMatch.OFPFW_DL_TYPE);
        }
        if (match.hasChild(OFMatch.STR_NW_TOS)) {
            DataNode nwTos = match.getChild(OFMatch.STR_NW_TOS);
            fmMatch.setNetworkTypeOfService((byte)nwTos.getLong());
            fmMatch.setWildcards(fmMatch.getWildcards() & ~OFMatch.OFPFW_NW_TOS);
        }
        if (match.hasChild(OFMatch.STR_NW_PROTO)) {
            DataNode nwProto = match.getChild(OFMatch.STR_NW_PROTO);
            fmMatch.setNetworkProtocol((byte)nwProto.getLong());
            fmMatch.setWildcards(fmMatch.getWildcards() & ~OFMatch.OFPFW_NW_PROTO);
        }
        if (match.hasChild(OFMatch.STR_NW_SRC)) {
            DataNode nwSrc = match.getChild(OFMatch.STR_NW_SRC);
            fmMatch.setFromCIDR(nwSrc.getString(), OFMatch.STR_NW_SRC);
        }
        if (match.hasChild(OFMatch.STR_NW_DST)) {
            DataNode nwDst = match.getChild(OFMatch.STR_NW_DST);
            fmMatch.setFromCIDR(nwDst.getString(), OFMatch.STR_NW_DST);
        }
        if (match.hasChild(OFMatch.STR_TP_SRC)) {
            DataNode tpSrc = match.getChild(OFMatch.STR_TP_SRC);
            fmMatch.setTransportSource((short)tpSrc.getLong());
            fmMatch.setWildcards(fmMatch.getWildcards() & ~OFMatch.OFPFW_TP_SRC);
        }
        if (match.hasChild(OFMatch.STR_TP_DST)) {
            DataNode tpDst = match.getChild(OFMatch.STR_TP_DST);
            fmMatch.setTransportDestination((short)tpDst.getLong());
            fmMatch.setWildcards(fmMatch.getWildcards() & ~OFMatch.OFPFW_TP_DST);
        }
     }

    @LogMessageDoc(level="ERROR",
            message="Could not decode action {action}",
            explanation="A static flow entry contained an invalid action",
            recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    private void parseActionList(DataNode actionListDn, OFFlowMod fm) throws BigDBException {
        if (actionListDn == null) return;
        try {
            int actionLen = 0;
            List<OFAction> actionList = new ArrayList<OFAction>();
            Iterator<DataNode> actionIter = actionListDn.iterator();
            while (actionIter.hasNext()) {
                DataNode action = actionIter.next();
                String aType = action.getChild("action-type").getString();
                if (aType.equals("output")) {
                    short port = (short) action.getChild("output-data").getChild("port").getLong();
                    short maxL = (short) action.getChild("output-data").getChild("max-length").getLong(OFActionOutput.MAX_LENGTH);
                    actionList.add(new OFActionOutput(port, maxL));
                    actionLen += OFActionOutput.MINIMUM_LENGTH;
                } else if (aType.equals("opaque-enqueue")) {
                    DataNode cdn = action.getChild("opaque-enqueue-data");
                    short port = (short) cdn.getChild("port").getLong();
                    int queueId = (int) cdn.getChild("queue-id").getLong();
                    actionList.add(new OFActionEnqueue(port, queueId));
                    actionLen += OFActionEnqueue.MINIMUM_LENGTH;
                } else if (aType.equals("set-vlan-id")) {
                    short vlanId = (short) action.getChild("set-vlan-id-data").getChild("vlan-id").getLong();
                    actionList.add(new OFActionVirtualLanIdentifier(vlanId));
                    actionLen += OFActionVirtualLanIdentifier.MINIMUM_LENGTH;
                } else if (aType.equals("set-vlan-pcp")) {
                    byte p = (byte) action.getChild("set-vlan-pcp-data").getChild("vlan-pcp").getLong();
                    actionList.add(new OFActionVirtualLanPriorityCodePoint(p));
                    actionLen += OFActionVirtualLanPriorityCodePoint.MINIMUM_LENGTH;
                } else if (aType.equals("strip-vlan")) {
                    actionList.add(new OFActionStripVirtualLan());
                    actionLen += OFActionStripVirtualLan.MINIMUM_LENGTH;
                } else if (aType.equals("set-dl-src")) {
                    String mac = action.getChild("set-dl-src-data").getChild("src-dl-addr").getString();
                    actionList.add(new OFActionDataLayerSource(HexString.fromHexString(mac)));
                    actionLen += OFActionDataLayerSource.MINIMUM_LENGTH;
                } else if (aType.equals("set-dl-dst")) {
                    String mac = action.getChild("set-dl-dst-data").getChild("dst-dl-addr").getString();
                    actionList.add(new OFActionDataLayerDestination(HexString.fromHexString(mac)));
                    actionLen += OFActionDataLayerDestination.MINIMUM_LENGTH;
                } else if (aType.equals("set-nw-src")) {
                    String ip = action.getChild("set-nw-src-data").getChild("src-nw-addr").getString();
                    actionList.add(new OFActionNetworkLayerSource(IPv4.toIPv4Address(ip)));
                    actionLen += OFActionNetworkLayerSource.MINIMUM_LENGTH;
                } else if (aType.equals("set-nw-dst")) {
                    String ip = action.getChild("set-nw-dst-data").getChild("dst-nw-addr").getString();
                    actionList.add(new OFActionNetworkLayerDestination(IPv4.toIPv4Address(ip)));
                    actionLen += OFActionNetworkLayerDestination.MINIMUM_LENGTH;
                } else if (aType.equals("set-nw-tos")) {
                    byte tos = (byte) action.getChild("set-nw-tos-data").getChild("tos").getLong();
                    actionList.add(new OFActionNetworkTypeOfService(tos));
                    actionLen += OFActionNetworkTypeOfService.MINIMUM_LENGTH;
                } else if (aType.equals("set-tp-src")) {
                    short tp = (short) action.getChild("set-tp-src-data").getChild("src-port").getLong();
                    actionList.add(new OFActionTransportLayerSource(tp));
                    actionLen += OFActionTransportLayerSource.MINIMUM_LENGTH;
                } else if (aType.equals("set-tp-dst")) {
                    short tp = (short) action.getChild("set-tp-dst-data").getChild("dst-port").getLong();
                    actionList.add(new OFActionTransportLayerDestination(tp));
                    actionLen += OFActionNetworkLayerDestination.MINIMUM_LENGTH;
                }
                fm.setActions(actionList);
                fm.setLengthU(OFFlowMod.MINIMUM_LENGTH + actionLen);
                // TODO - handle vendor
            }
        } catch (Exception e) {
            throw new BigDBException("Could not parse action list," + e.getMessage());
        }
    }

    private Map<String, Map<String, OFFlowMod>> readEntriesFromBigDB() {
        Map<String, Map<String, OFFlowMod>> entries =
                new ConcurrentHashMap<String, Map<String, OFFlowMod>>();
        try {
            Query query = Query.parse("/core/switch");
            DataNodeSet switchDataNodes =
                    bigDBService.getControllerTreespace().queryData(query, AuthContext.SYSTEM);
            for (DataNode switchDataNode: switchDataNodes) {
                // If the actual flow entry or dpid are missing skip it for now
                if (!switchDataNode.hasChild("static-flow-entry") || !switchDataNode.hasChild("dpid"))
                    continue;
                DataNode flowEntryListDataNode = switchDataNode.getChild("static-flow-entry");
                DataNode dpidDataNode = switchDataNode.getChild("dpid");
                String dpidString = dpidDataNode.getString();
                parseFlowEntryListDataNode(dpidString, flowEntryListDataNode, entries);
            }
            return entries;
        }
        catch (DataNodeNotFoundException e) {
            // No switch data has been set, but that's OK so don't treat
            // this as an error.
        }
        catch (Exception e) {
            log.error("failed to load static flow entries from BigDB: {}",
                    e.getMessage());
        }

        return entries;
    }

    @Override
    public void dataNodesMutated(Set<Query> mutatedNodes, Operation operation, AuthContext authContext)
            throws BigDBException {
        for (Query query: mutatedNodes) {
            List<Step> steps = query.getSteps();
            int stepCount = steps.size();
            String dpidString = null;
            Step switchStep = (stepCount > 1) ? steps.get(1) : null;
            if (switchStep != null)
                dpidString = switchStep.getExactMatchPredicateString("dpid");
            if (dpidString == null)
                throw new UnsupportedOperationException();
            String flowEntryName = null;
            Step flowEntryStep = (stepCount > 2) ? steps.get(2) : null;
            if (flowEntryStep != null)
                flowEntryName = flowEntryStep.getExactMatchPredicateString("name");

            switch (operation) {
            case MODIFY:
                String queryString;
                if (flowEntryName != null) {
                    queryString = String.format("/core/switch[dpid=\"%s\"]/static-flow-entry[name=\"%s\"]", dpidString, flowEntryName);
                } else {
                    queryString = String.format("/core/switch[dpid=\"%s\"]/static-flow-entry", dpidString);
                }
                Query flowQuery = Query.parse(queryString);
                DataNodeSet dataNodeSet =
                        bigDBService.getControllerTreespace().queryData(flowQuery, AuthContext.SYSTEM);
                if (!dataNodeSet.isEmpty()) {
                    Map<String, Map<String, OFFlowMod>> entriesToAdd =
                            new HashMap<String, Map<String, OFFlowMod>>();
                    if (flowEntryName != null) {
                        parseFlowEntryDataNode(dpidString, flowEntryName, dataNodeSet.getSingleDataNode(), entriesToAdd);
                    } else {
                        parseFlowEntryListDataNode(dpidString, dataNodeSet, entriesToAdd);
                    }
                    // batch updates by switch and blast them out
                    for (Entry<String, Map<String, OFFlowMod>> entr : entriesToAdd.entrySet()) {
                        String dpid = entr.getKey();
                        if (!entriesFromStorage.containsKey(dpid))
                            entriesFromStorage.put(dpid, new HashMap<String, OFFlowMod>());
                        List<OFMessage> outQueue = new ArrayList<OFMessage>();
                        for (Entry<String, OFFlowMod> entry : entr.getValue().entrySet()) {
                            OFFlowMod newFlowMod = entry.getValue();
                            OFFlowMod oldFlowMod = entriesFromStorage.get(dpid).get(entry.getKey());
                            // remove any pre-existing rule
                            // TODO - optimize this to see if we can just update the flowmod
                            if (oldFlowMod != null) {
                                oldFlowMod.setCommand(OFFlowMod.OFPFC_DELETE_STRICT);
                                outQueue.add(oldFlowMod);
                            }
                            if (newFlowMod != null) {
                                entriesFromStorage.get(dpid).put(entry.getKey(), newFlowMod);
                                outQueue.add(newFlowMod);
                            } else {
                                entriesFromStorage.get(dpid).remove(entry.getKey());
                            }
                        }
                        writeOFMessagesToSwitch(HexString.toLong(dpid), outQueue);
                    }
                }
                break;
            case DELETE:
                if (flowEntryName != null) {
                    // delete a specific flow
                    Map<String, OFFlowMod> switchFlowEntries = entriesFromStorage.get(dpidString);
                    if (switchFlowEntries != null) {
                        OFFlowMod deleteFlowMod = switchFlowEntries.remove(flowEntryName);
                        if (deleteFlowMod != null) {
                            deleteFlowMod.setCommand(OFFlowMod.OFPFC_DELETE_STRICT);
                            writeFlowModToSwitch(HexString.toLong(dpidString), deleteFlowMod);
                        }
                    }
                } else {
                    // delete all flows for a switch
                    Map<String, OFFlowMod> switchFlows = entriesFromStorage.remove(dpidString);
                    List<OFMessage> outQueue = new ArrayList<OFMessage>();
                    if (switchFlows != null) {
                        for (OFFlowMod fm : switchFlows.values()) {
                            fm.setCommand(OFFlowMod.OFPFC_DELETE_STRICT);
                            outQueue.add(fm);
                        }
                        writeOFMessagesToSwitch(HexString.toLong(dpidString), outQueue);
                    }
                }
                break;
            }
        }
    }

    @Override
    public void switchAdded(long switchId) {
        log.debug("Switch {} connected; processing its static entries",
                  HexString.toHexString(switchId));
        sendEntriesToSwitch(switchId);
    }

    @Override
    public void switchRemoved(long switchId) {
        // do NOT delete from our internal state; we're tracking the rules,
        // not the switches
    }

    @Override
    public void switchActivated(long switchId) {
        // no-op
    }

    @Override
    public void switchChanged(long switchId) {
        // no-op
    }

    @Override
    public void switchPortChanged(long switchId,
                                  ImmutablePort port,
                                  IOFSwitch.PortChangeType type) {
        // no-op
    }

    /**
     * Writes a list of OFMessages to a switch
     * @param dpid The datapath ID of the switch to write to
     * @param messages The list of OFMessages to write.
     */
    @LogMessageDoc(level="ERROR",
            message="Tried to write to switch {switch} but got {error}",
            explanation="An I/O error occured while trying to write a " +
                    "static flow to a switch",
            recommendation=LogMessageDoc.CHECK_SWITCH)
    private void writeOFMessagesToSwitch(long dpid, List<OFMessage> messages) {
        IOFSwitch ofswitch = floodlightProvider.getSwitch(dpid);
        if (ofswitch != null) {  // is the switch connected
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Sending {} new entries to {}", messages.size(), HexString.toHexString(dpid));
                }
                ofswitch.write(messages, null);
                ofswitch.flush();
            } catch (IOException e) {
                log.error("Tried to write to switch {} but got {}", HexString.toHexString(dpid), e.getMessage());
            }
        }
    }

    /**
     * Writes an OFFlowMod to a switch. It checks to make sure the switch
     * exists before it sends
     * @param dpid The data  to write the flow mod to
     * @param flowMod The OFFlowMod to write
     */
    private void writeFlowModToSwitch(long dpid, OFFlowMod flowMod) {
        IOFSwitch ofSwitch = floodlightProvider.getSwitch(dpid);
        if (ofSwitch == null) {
            if (log.isDebugEnabled()) {
                log.debug("Not writing flowmod because switch {} is not connected", dpid);
            }
            return;
        }
        writeFlowModToSwitch(ofSwitch, flowMod);
    }

    /**
     * Writes an OFFlowMod to a switch
     * @param sw The IOFSwitch to write to
     * @param flowMod The OFFlowMod to write
     */
    @LogMessageDoc(level="ERROR",
            message="Tried to write OFFlowMod to {switch} but got {error}",
            explanation="An I/O error occured while trying to write a " +
                    "static flow to a switch",
            recommendation=LogMessageDoc.CHECK_SWITCH)
    private void writeFlowModToSwitch(IOFSwitch sw, OFFlowMod flowMod) {
        try {
            sw.write(flowMod, null);
            sw.flush();
        } catch (IOException e) {
            log.error("Tried to write OFFlowMod to {} but failed: {}",
                    HexString.toHexString(sw.getId()), e.getMessage());
        }
    }

    @Override
    public String getName() {
        return StaticFlowName;
    }

    /**
     * Handles a flow removed message from a switch. If the flow was removed
     * and we did not explicitly delete it we re-install it. If we explicitly
     * removed the flow we stop the processing of the flow removed message.
     * @param sw The switch that sent the flow removed message.
     * @param msg The flow removed message.
     * @param cntx The associated context.
     * @return Whether to continue processing this message.
     */
    public Command handleFlowRemoved(IOFSwitch sw, OFFlowRemoved msg, FloodlightContext cntx) {
        long cookie = msg.getCookie();
        /**
         * This is just to sanity check our assumption that static flows
         * never expire.
         */
        if (AppCookie.extractApp(cookie) == STATIC_FLOW_APP_ID) {
            if (msg.getReason() != OFFlowRemoved.OFFlowRemovedReason.OFPRR_DELETE)
                log.error("Got a FlowRemove message for a infinite " +
                          "timeout flow: {} from switch {}", msg, sw);
            // Stop the processing chain since we sent the delete.
            return Command.STOP;
        }

        return Command.CONTINUE;
    }

    @Override
    @LogMessageDoc(level="ERROR",
        message="Got a FlowRemove message for a infinite " +
                "timeout flow: {flow} from switch {switch}",
        explanation="Flows with infinite timeouts should not expire. " +
                "The switch has expired the flow anyway.",
        recommendation=LogMessageDoc.REPORT_SWITCH_BUG)
    @SuppressFBWarnings(value="BC_UNCONFIRMED_CAST")
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
        case FLOW_REMOVED:
            return handleFlowRemoved(sw, (OFFlowRemoved) msg, cntx);
        default:
            return Command.CONTINUE;
        }
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;  // no dependency for non-packet in
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;  // no dependency for non-packet in
    }

    // IFloodlightModule

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IStaticFlowEntryPusherService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
            IFloodlightService> m =
                new HashMap<Class<? extends IFloodlightService>,
                    IFloodlightService>();
        m.put(IStaticFlowEntryPusherService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IBigDBService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        floodlightProvider =
            context.getServiceImpl(IFloodlightProviderService.class);

        bigDBService =
            context.getServiceImpl(IBigDBService.class);
        haListener = new HAListenerDelegate();
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
        floodlightProvider.addOFSwitchListener(this);
        floodlightProvider.addHAListener(this.haListener);

        try {
            treespace = bigDBService.getControllerTreespace();
            Query staticFlowEntryQuery =
                    Query.parse("/core/switch/static-flow-entry");
            treespace.registerMutationListener(staticFlowEntryQuery, true, this);
        } catch (BigDBException e) {
            log.error("Error registering BigDB mutation listener", e);
        }
        entriesFromStorage = readEntriesFromBigDB();
    }

    // IStaticFlowEntryPusherService methods

    @Override
    public void addFlow(String name, OFFlowMod fm, String swDpid) throws BigDBException {
        // FIXME: re-implement using BigDB serializers
        // We check if the entry exists with this name and format our query string
        // appropriately.
        String queryString = null;
        // Check to see if there's an existing switch element in the config
        Query switchQuery = Query.parse("/core/switch[dpid=$dpid]", "dpid", swDpid);
        DataNodeSet queryResult = treespace.queryData(switchQuery, null);
        if (queryResult.isEmpty()) {
            // No config for the switch so create that first
            String switchData = String.format("{\"dpid\":\"%s\"}", swDpid);
            try {
                InputStream switchInputStream =
                        new ByteArrayInputStream(switchData.getBytes("UTF-8"));
                treespace.insertData(Query.parse("/core/switch"),
                        Treespace.DataFormat.JSON, switchInputStream, null);
            }
            catch (UnsupportedEncodingException e) {
                throw new BigDBException("Invalid switch data", e);
            }
            catch (BigDBException e) {
                // Ignore conflict errors, i.e. that the switch config already
                // exists. This should happen rarely because we already checked
                // that it didn't exist above, but it could happen if another
                // thread added a flow for the same switch between the query
                // and insert operations. In that case, it's safe to ignore the
                // error, since it's ok if someone else created it.
                if (e.getErrorType() != BigDBException.Type.CONFLICT)
                    throw e;
            }
        }
        // Now we can create the static flow entry
        queryString = String.format("/core/switch[dpid=\"%s\"]/static-flow-entry[name=\"%s\"]", swDpid, name);
        Query query = Query.parse(queryString);
        StaticFlowEntry sfe = new StaticFlowEntry(name, swDpid, fm, true);
        ObjectMapper mapper = new ObjectMapper();
        try {
            String jsonData = mapper.writeValueAsString(sfe);
            InputStream inputStream =
                    new ByteArrayInputStream(jsonData.getBytes("UTF-8"));
            treespace.replaceData(query, Treespace.DataFormat.JSON, inputStream, null);
        } catch (JsonGenerationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JsonMappingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void deleteFlow(String name, String dpid) throws BigDBException {
        if ((name == null) || (dpid == null)) return;
        if (log.isDebugEnabled()) {
            log.debug("Deleting static flow {} from switch {}", name, dpid);
        }

        String queryString =
            String.format("/core/switch[dpid=\"%s\"]/static-flow-entry[name=\"%s\"]", dpid, name);
        Query query = Query.parse(queryString);
        treespace.deleteData(query, null);
    }

    @Override
    public void deleteAllFlows() throws BigDBException {
        for (String dpid : entriesFromStorage.keySet()) {
            deleteFlowsForSwitch(dpid);
        }
    }

    @Override
    public void deleteFlowsForSwitch(String dpid) throws BigDBException {
        if (log.isDebugEnabled()) {
            log.debug("Deleting all static flows for switch {}", dpid);
        }
        String queryString =
            String.format("/core/switch[dpid=\"%s\"]/static-flow-entry", dpid);
        Query query = Query.parse(queryString);
        treespace.deleteData(query, null);
    }

    @Override
    public Map<String, Map<String, OFFlowMod>> getFlows() {
        return entriesFromStorage;
    }

    @Override
    public Map<String, OFFlowMod> getFlows(String dpid) {
        return entriesFromStorage.get(dpid);
    }

    // IHAListener
    private class HAListenerDelegate implements IHAListener {
        @Override
        public void transitionToMaster() {
            log.debug("Re-reading static flows from storage due " +
                    "to HA change from SLAVE->MASTER");
            entriesFromStorage = readEntriesFromBigDB();
        }

        @Override
        public String getName() {
            return StaticFlowEntryPusher.this.getName();
        }

        @Override
        public boolean isCallbackOrderingPrereq(HAListenerTypeMarker type,
                                                String name) {
            return false;
        }

        @Override
        public boolean isCallbackOrderingPostreq(HAListenerTypeMarker type,
                                                 String name) {
            return false;
        }

        @Override
        public void controllerNodeIPsChanged(Map<String, String> curControllerNodeIPs,
                                             Map<String, String> addedControllerNodeIPs,
                                             Map<String, String> removedControllerNodeIPs) {
        }
    }
}
