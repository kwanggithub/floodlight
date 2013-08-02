package net.floodlightcontroller.topology.bigdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import net.bigdb.BigDBException;
import net.bigdb.data.annotation.BigDBPath;
import net.bigdb.data.annotation.BigDBProperty;
import net.bigdb.data.annotation.BigDBQuery;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.types.NodeInterfaceTuple;
import net.floodlightcontroller.core.types.PortInterfacePair;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LinkDirection;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LinkType;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topology.BroadcastDomain;
import net.floodlightcontroller.topology.Cluster;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.topology.OrderedNodePair;
import net.floodlightcontroller.topology.bigdb.LinkWithType.SwitchInterface;
import net.floodlightcontroller.util.FilterIterator;

import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyResource {

    protected static Logger log = 
            LoggerFactory.getLogger(TopologyResource.class);

    protected ITopologyService topologyService;
    protected ILinkDiscoveryService linkService;
    protected IFloodlightProviderService floodlightProvider;
    protected IRoutingService routingService;

    public TopologyResource(ITopologyService tp, ILinkDiscoveryService ls,
                            IFloodlightProviderService ifl, IRoutingService rs)
                    throws BigDBException {
        super();
        this.topologyService = tp;
        this.linkService = ls;
        this.floodlightProvider = ifl;
        this.routingService = rs;
    }

    @BigDBQuery
    @BigDBPath("link")
    public List<LinkWithType> getLinks() {
        Iterator<LinkWithType> allLinkIter = getDebugLinks().iterator();
        FilterIterator<LinkWithType> linkIter = 
                new FilterIterator<LinkWithType>(allLinkIter) {
            @Override
            protected boolean matches(LinkWithType value) {
                if (LinkType.DIRECT_LINK.equals(value.getType()))
                    return true;

                return false;
            }
        };

        List<LinkWithType> resultList = new ArrayList<LinkWithType>();

        while (linkIter.hasNext()) {
            resultList.add(linkIter.next());
        }

        return resultList;
    }

    @BigDBQuery
    @BigDBPath("debug/link")
    public List<LinkWithType> getDebugLinks() {
        List<LinkWithType> returnLinkSet = new ArrayList<LinkWithType>();

        for (Link link: linkService.getLinks().keySet()) {
            LinkInfo info = linkService.getLinkInfo(link);
            LinkType type = linkService.getLinkType(link, info);
            LinkDirection d = LinkDirection.BIDIRECTIONAL;
            if (LinkType.TUNNEL.equals(info.getLinkType())) {
                d = LinkDirection.UNIDIRECTIONAL;
            }
            String srcName = null;
            String dstName = null;

            IOFSwitch sw = floodlightProvider.getSwitch(link.getSrc());
            if (sw != null) {
                ImmutablePort port = sw.getPort(link.getSrcPort());
                if (port != null) {
                    srcName = port.getName();
                }
            }
            sw = floodlightProvider.getSwitch(link.getDst());
            if (sw != null) {
                ImmutablePort port = sw.getPort(link.getDstPort());
                if (port != null) {
                    dstName = port.getName();
                }
            }
            SwitchInterface src = 
                    new SwitchInterface(link.getSrc(), 
                                        new PortInterfacePair(link.getSrcPort(), 
                                                              srcName));
            SwitchInterface dst = 
                    new SwitchInterface(link.getDst(), 
                                        new PortInterfacePair(link.getDstPort(), 
                                                              dstName));
            LinkWithType lwt = 
                    new LinkWithType(src, dst, type, d);
            returnLinkSet.add(lwt);
        }
        return returnLinkSet;
    }

    /**
     * An innter class used for BigDB serialization.
     * @author alexreimers
     *
     */
    public static class EnabledInterfaceResult {
        PortInterfacePair pip;
        String dpid;

        public EnabledInterfaceResult(String dpid, PortInterfacePair pip) {
            this.dpid = dpid;
            this.pip = pip;
        }

        @BigDBProperty("switch-dpid")
        public String getDpid() {
            return dpid;
        }

        @BigDBProperty("interface")
        public PortInterfacePair getInterface() {
            return pip;
        }
    }

    @BigDBQuery
    @BigDBPath("debug/enabled-interface")
    public List<EnabledInterfaceResult> getEnabledPorts() {
        Set<Long> switches = floodlightProvider.getAllSwitchDpids();
        if (switches == null) return null;

        List<EnabledInterfaceResult> result = new ArrayList<EnabledInterfaceResult>(switches.size());
        for (long sw: switches) {
            Set<Short> ports = topologyService.getPorts(sw);
            if (ports == null) continue;
            String dpid = HexString.toHexString(sw);
            for (short p: ports) {
                String pName = floodlightProvider.getSwitch(sw).getPort(p).getName();
                result.add(new EnabledInterfaceResult(dpid, new PortInterfacePair(p, pName)));
            }
        }
        return result;
    }

    public static class SwitchCluster {

        private List<String> switches;

        @BigDBProperty(value="member")
        public List<String> getMembers() {
            return switches;
        }

        public SwitchCluster(List<String> m) {
            this.switches = m;
        }
    }

    @BigDBQuery
    @BigDBPath("switch-cluster")
    public List<SwitchCluster> getSwitchClusters() {
        // DPID-key -> List<DPID-members>
        Map<String, List<String>> scMap = new HashMap<String, List<String>>();
        for (Long dpid : floodlightProvider.getAllSwitchDpids()) {
            String clusterDpid = HexString.toHexString(
                    topologyService.getL2DomainId(dpid));
            String swDpid = HexString.toHexString(dpid);
            List<String> switchesInCluster = scMap.get(clusterDpid);
            if (switchesInCluster == null)
                scMap.put(clusterDpid,
                          switchesInCluster = new ArrayList<String>());
            switchesInCluster.add(swDpid);
        }

        List<SwitchCluster> clusterList = new ArrayList<SwitchCluster>();
        for (List<String> cluster: scMap.values()) {
            clusterList.add(new SwitchCluster(cluster));
        }

        return clusterList;
    }

    @BigDBQuery
    @BigDBPath("external-interface")
    public List<EnabledInterfaceResult> getBroadcastDomainPorts() {
        Set<NodePortTuple> bdPorts = topologyService.getBroadcastDomainPorts();

        if (bdPorts == null) return null;

        List<EnabledInterfaceResult> result = new ArrayList<EnabledInterfaceResult>(bdPorts.size());
        for (NodePortTuple npt : bdPorts) {
            long swId = npt.getNodeId();
            short pId = npt.getPortId();
            String dpid = HexString.toHexString(swId);
            String pName = floodlightProvider.getSwitch(swId).
                    getPort(pId).getName();
            result.add(new EnabledInterfaceResult(dpid, new PortInterfacePair(pId, pName)));
        }

        return result;
    }

    public static class BroadcastDomainWithInterface {
        String id;
        List<NodeInterfaceTuple> interfaces;

        public BroadcastDomainWithInterface(long id) {
            this.id = HexString.toHexString(id);
            this.interfaces = new ArrayList<NodeInterfaceTuple>();
        }

        public BroadcastDomainWithInterface(long id, List<NodeInterfaceTuple> intfs) {
            this.id = HexString.toHexString(id);
            this.interfaces = intfs;
        }

        public void addInterface(NodeInterfaceTuple nit) {
            this.interfaces.add(nit);
        }

        @BigDBProperty("switch-dpid")
        public String getId() {
            return id;
        }

        @BigDBProperty("interface")
        public List<NodeInterfaceTuple> getInterfaces() {
            return interfaces;
        }
    }

    @BigDBQuery
    @BigDBPath("external-broadcast-interface")
    public List<BroadcastDomainWithInterface> 
        getExternalBroadcastDomainInterfaces() {
        Set<BroadcastDomain> bDomains = topologyService.getBroadcastDomains();
        if (bDomains == null) return null;

        List<BroadcastDomainWithInterface> result = 
                new ArrayList<BroadcastDomainWithInterface>(bDomains.size());
        for (BroadcastDomain bd : bDomains) {
            BroadcastDomainWithInterface bdwi = 
                    new BroadcastDomainWithInterface(bd.getId());
            for (NodePortTuple npt : bd.getPorts()) {
                String intfName = 
                        floodlightProvider.getSwitch(npt.getNodeId())
                                .getPort(npt.getPortId()).getName();
                NodeInterfaceTuple nit = new NodeInterfaceTuple(npt, intfName);
                bdwi.addInterface(nit);
            }
            result.add(bdwi);
        }

        return result;
    }

    public static class AllowedUnicastPort {
        long srcId;
        long dstId;
        Set<NodePortTuple> ports;

        public AllowedUnicastPort(long src, long dst, Set<NodePortTuple> ports) {
            this.srcId = src;
            this.dstId = dst;
            this.ports = ports;
        }

        @BigDBProperty("src-id")
        public long getSrc() {
            return srcId;
        }

        @BigDBProperty("dst-id")
        public long getDst() {
            return dstId;
        }

        @BigDBProperty("port")
        public Set<NodePortTuple> getPorts() {
            return ports;
        }
    }

    @BigDBQuery
    @BigDBPath("debug/allowed-port/unicast")
    public List<AllowedUnicastPort> getDebugAllowedPortsUnicast() {
        Map<OrderedNodePair, Set<NodePortTuple>> ports = topologyService.getAllowedUnicastPorts();
        if (ports.isEmpty()) return null;

        List<AllowedUnicastPort> pList = new ArrayList<AllowedUnicastPort>(ports.size());
        for (Entry<OrderedNodePair, Set<NodePortTuple>> e : ports.entrySet()) {
            pList.add(new AllowedUnicastPort(e.getKey().getSrc(), e.getKey().getDst(), e.getValue()));
        }
        return pList;
    }

    public static class AllowedBroadcastPort {
        long srcId;
        long dstId;
        NodePortTuple port;

        public AllowedBroadcastPort(long src, long dst, NodePortTuple npt) {
            this.srcId = src;
            this.dstId = dst;
            this.port = npt;
        }

        @BigDBProperty("src-id")
        public long getSrc() {
            return srcId;
        }

        @BigDBProperty("dst-id")
        public long getDst() {
            return dstId;
        }

        @BigDBProperty("port")
        public NodePortTuple getPort() {
            return port;
        }
    }

    @BigDBQuery
    @BigDBPath("debug/allowed-port/broadcast")
    public List<AllowedBroadcastPort> getDebugAllowedPortsBroadcast() {
        Map<OrderedNodePair, NodePortTuple> ports = topologyService.getAllowedIncomingBroadcastPorts();
        if (ports.isEmpty()) return null;

        List<AllowedBroadcastPort> pList = new ArrayList<AllowedBroadcastPort>(ports.size());
        for (Entry<OrderedNodePair, NodePortTuple> e : ports.entrySet()) {
            pList.add(new AllowedBroadcastPort(e.getKey().getSrc(), e.getKey().getDst(), e.getValue()));
        }
        return pList;
    }

    public static class AllowedExternalPort {
        NodePortTuple src;
        Set<Long> id;

        public AllowedExternalPort(NodePortTuple src, Set<Long> ids) {
            this.src = src;
            this.id = ids;
        }

        @BigDBProperty("switch-dpid")
        public String getDpid() {
            return HexString.toHexString(src.getNodeId());
        }

        @BigDBProperty("port")
        public int getPort() {
            return src.getPortId();
        }

        @BigDBProperty("id")
        public Set<Long> getDpids() {
            return id;
        }
    }

    @BigDBQuery
    @BigDBPath("debug/allowed-port/external")
    public List<AllowedExternalPort> getDebugAllowedPortsExternal() {
        Map<NodePortTuple, Set<Long>> ports = topologyService.getAllowedPortToBroadcastDomains();
        if (ports.isEmpty()) return null;

        List<AllowedExternalPort> pList = new ArrayList<AllowedExternalPort>(ports.size());
        for (Entry<NodePortTuple, Set<Long>> e : ports.entrySet()) {
            pList.add(new AllowedExternalPort(e.getKey(), e.getValue()));
        }
        return pList;
    }

    public static class TopologyDomainOpenFlow {
        private long id;
        private Cluster cluster;

        public TopologyDomainOpenFlow(long id, Cluster c) {
            this.id = id;
            this.cluster = c;
        }

        @BigDBProperty("id")
        public long getId() {
            return id;
        }

        @BigDBProperty("cluster")
        public Cluster getCluster() {
            return cluster;
        }
    }

    @BigDBQuery
    @BigDBPath("debug/higher-topology/domain/openflow")
    public List<TopologyDomainOpenFlow> getHigherTopologyDomainOpenFlow() {
        Map<Long, Object> htNodesMap = topologyService.getHigherTopologyNodes();
        if (htNodesMap == null) return null;
        List<TopologyDomainOpenFlow> l = new ArrayList<TopologyDomainOpenFlow>(htNodesMap.size());

        for (long nid: htNodesMap.keySet()) {
            Object x = htNodesMap.get(nid);
            if (x instanceof Cluster)
                l.add(new TopologyDomainOpenFlow(nid, (Cluster)x));
        }
        return l;
    }

    public static class TopologyDomainBroadcast {
        private long id;
        private BroadcastDomain bd;

        public TopologyDomainBroadcast(long id, BroadcastDomain bd) {
            this.id = id;
            this.bd = bd;
        }

        @BigDBProperty("id")
        public long getId() {
            return id;
        }

        @BigDBProperty("broadcast-domain")
        public BroadcastDomain getBd() {
            return bd;
        }
    }

    @BigDBQuery
    @BigDBPath("debug/higher-topology/domain/broadcast")
    public List<TopologyDomainBroadcast> getHigherTopologyDomainBroadcast() {
        Map<Long, Object> htNodesMap = topologyService.getHigherTopologyNodes();
        if (htNodesMap == null) return null;
        List<TopologyDomainBroadcast> bdList = new ArrayList<TopologyDomainBroadcast>(htNodesMap.size());

        for (long nid: htNodesMap.keySet()) {
            Object x = htNodesMap.get(nid);
            if (x instanceof BroadcastDomain)
                bdList.add(new TopologyDomainBroadcast(nid, (BroadcastDomain)x));
        }

        return bdList;
    }

    public static class HigherTopologyNeighbor {
        private long id;
        private Set<Long> neighborId;

        public HigherTopologyNeighbor(long id, Set<Long> nid) {
            this.id = id;
            this.neighborId = nid;
        }

        @BigDBProperty("id")
        public long getId() {
            return id;
        }

        @BigDBProperty("neighbor-id")
        public Set<Long> getNeighborId() {
            return neighborId;
        }
    }

    @BigDBQuery
    @BigDBPath("debug/higher-topology/neighbor")
    public List<HigherTopologyNeighbor>  getHigherTopologyNeighbors() {
        Map<Long, Set<Long>> nMap = topologyService.getHigherTopologyNeighbors();
        if (nMap == null || nMap.isEmpty())
            return null;

        List<HigherTopologyNeighbor> nList = new ArrayList<HigherTopologyNeighbor>(nMap.size());
        for (Entry<Long, Set<Long>> e : nMap.entrySet()) {
            nList.add(new HigherTopologyNeighbor(e.getKey(), e.getValue()));
        }
        return nList;
    }

    public static class HigherTopologyNextHop {
        private long srcId;
        private List<NextHop> nHops;

        public static class NextHop {
            private long dstId;
            private long nextHopId;

            public NextHop(long dstId, long nextHopId) {
                this.dstId = dstId;
                this.nextHopId = nextHopId;
            }

            @BigDBProperty("dst-id")
            public long getDstId() {
                return dstId;
            }

            @BigDBProperty("next-hop-id")
            public long getNextHop() {
                return nextHopId;
            }
        }

        public HigherTopologyNextHop(long src, Map<Long, Long> nextHop) {
            this.srcId = src;
            this.nHops = new ArrayList<NextHop>(nextHop.size());
            for (Entry<Long, Long> e : nextHop.entrySet()) {
                nHops.add(new NextHop(e.getKey(), e.getValue()));
            }
        }

        @BigDBProperty("src-id")
        public long getSrcId() {
            return srcId;
        }

        @BigDBProperty("dst-next-hop")
        public List<NextHop> getNextHops() {
            return nHops;
        }
    }

    @BigDBQuery
    @BigDBPath("debug/higher-topology/next-hop")
    public List<HigherTopologyNextHop>  getHigherTopologyNextHop() {
        Map<Long, Map<Long, Long>> nMap = topologyService.getHigherTopologyNextHops();
        if (nMap == null || nMap.isEmpty())
            return null;

        List<HigherTopologyNextHop> nList = new ArrayList<HigherTopologyNextHop>(nMap.size());
        for (Entry<Long, Map<Long, Long>> e : nMap.entrySet()) {
            nList.add(new HigherTopologyNextHop(e.getKey(), e.getValue()));
        }
        return nList;
    }

    public static class LayerTwoDomain {
        private String clusterId;
        private String l2domainId;

        public LayerTwoDomain(long dpid, long cId) {
            this.clusterId = HexString.toHexString(dpid);
            this.l2domainId = HexString.toHexString(cId);
        }

        @BigDBProperty("cluster-id")
        public String getSwitchDpid() {
            return clusterId;
        }

        @BigDBProperty("layer-two-domain-id")
        public String getClusterId() {
            return l2domainId;
        }
    }

    @BigDBQuery
    @BigDBPath("debug/higher-topology/layer-two-domain")
    public List<LayerTwoDomain> getHigherTopologyLayerTwoDomain() {
        Map<Long, Long> nMap = topologyService.getL2DomainIds();
        if (nMap == null || nMap.isEmpty())
            return null;

        List<LayerTwoDomain> lList = new ArrayList<LayerTwoDomain>(nMap.size());
        for (Entry<Long, Long> e : nMap.entrySet()) {
            lList.add(new LayerTwoDomain(e.getKey(), e.getValue()));
        }

        return lList;
    }
}
