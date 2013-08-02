package org.sdnplatform.sync.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.annotations.LogMessageDoc;

import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;
import org.sdnplatform.sync.ClusterNode;
import org.sdnplatform.sync.IClusterListener;
import org.sdnplatform.sync.IClusterService;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.ObsoleteVersionException;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.error.UnknownStoreException;
import org.sdnplatform.sync.internal.config.AuthScheme;
import org.sdnplatform.sync.internal.config.ClusterConfig;
import org.sdnplatform.sync.internal.config.SyncStoreCCProvider;
import org.sdnplatform.sync.thrift.AsyncMessageHeader;
import org.sdnplatform.sync.thrift.LeaderAckMessage;
import org.sdnplatform.sync.thrift.LeaderCandMessage;
import org.sdnplatform.sync.thrift.LeaderMessage;
import org.sdnplatform.sync.thrift.MessageType;
import org.sdnplatform.sync.thrift.SyncMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.net.HostAndPort;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class ClusterManager implements IClusterService {
    protected static final Logger logger =
            LoggerFactory.getLogger(ClusterManager.class.getName());
    
    private SyncManager syncManager;
    private List<IClusterListener> listeners = new CopyOnWriteArrayList<>();
    
    private Object electionLock = new Object();

    private volatile ElectionState electionState = ElectionState.INIT;
    private Short topCandidate;
    private int topCandidateWeight;
    private Short leaderNodeId;
    private Set<Short> currentSet = new HashSet<>();
    private Timeout currentTimeout;
    private boolean riggedElection = false;
    
    /**
     * Timeout for leader election phases
     */
    private static final long ELECTION_TIMEOUT = 1000;
    
    public ClusterManager(SyncManager syncManager) {
        super();
        this.syncManager = syncManager;
    }

    // ***************
    // IClusterService
    // ***************
    
    @Override
    public void setAuthInfo(AuthScheme authScheme,
                            String keyStorePath,
                            String keyStorePassword) 
                                    throws SyncException {
        setAuthInfo(getUnsyncStore(), 
                    authScheme, 
                    keyStorePath, 
                    keyStorePassword);
    }

    @Override
    public void setSeeds(String seeds) 
            throws SyncException, IllegalArgumentException {
        setSeeds(getUnsyncStore(), seeds);
    }

    @Override
    public void reseed() throws SyncException {
        Short localNodeId = getLocalNodeIdOrThrow();
        reseed(getNodeStore(), localNodeId);
    }

    @Override
    public void setLocalDomainId(short domainId) throws SyncException {
        Short localNodeId = getLocalNodeIdOrThrow();
        setLocalDomainId(getNodeStore(), localNodeId, domainId);
    }

    @Override
    public void setLocalNodePort(int port) throws SyncException {
        setLocalNodePort(getUnsyncStore(), port);
    }

    @Override
    public void setLocalNodeIface(String ifaceName) throws SyncException {
        setLocalNodeIface(getUnsyncStore(), ifaceName);
    }

    @Override
    public void setLocalNodeHost(String hostname) throws SyncException {
        setLocalNodeHost(getUnsyncStore(), hostname);
    }

    @Override
    public void deleteNode(short nodeId) throws SyncException {
        Short localNodeId = getLocalNodeIdOrThrow();
        if (localNodeId.equals(nodeId))
            throw new SyncException("Cannot delete current node from cluster");
        
        deleteNode(getNodeStore(), nodeId);
    }

    @Override
    public short getLocalNodeId() {
        return syncManager.getClusterConfig().getNode().getNodeId();
    }
    
    @Override
    public Collection<ClusterNode> getNodes() {
        return syncManager.getClusterConfig().getNodes();
    }
    
    @Override
    public boolean isConnected(short nodeId) {
        return syncManager.rpcService.isConnected(nodeId);
    }

    @Override
    public String getKeystorePath() {
        return syncManager.getClusterConfig().getKeyStorePath();
    }

    @Override
    public String getKeystorePassword() {
        return syncManager.getClusterConfig().getKeyStorePassword();
    }

    @Override
    public Short getDomainLeader() {
        return leaderNodeId;
    }
    
    @Override
    public void registerListener(IClusterListener listener) {
        listeners.add(listener);
    }

    @Override
    public void newElection(boolean rigged) {
        riggedElection = rigged;
        try {
            beginElection();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    
    // *****************************
    // ClusterService static methods
    // *****************************

    public static void setLocalNodePort(IStoreClient<String, 
                                                     String> uStoreClient,
                                        int port) throws SyncException {
        while (true) {
            try {
                uStoreClient.put(SyncStoreCCProvider.LOCAL_NODE_IFACE,
                                 Integer.toString(port));
                break;
            } catch (ObsoleteVersionException e) {}
        }
    }

    @SuppressFBWarnings(value="DE_MIGHT_IGNORE")
    public static void setLocalNodeIface(IStoreClient<String, 
                                                      String> uStoreClient,
                                         String iface) throws SyncException {
        while (true) {
            try {
                uStoreClient.put(SyncStoreCCProvider.LOCAL_NODE_IFACE,
                                 iface);
                break;
            } catch (ObsoleteVersionException e) {}
        }
    }

    @SuppressFBWarnings(value="DE_MIGHT_IGNORE")
    public static void setLocalNodeHost(IStoreClient<String, 
                                                     String> uStoreClient,
                                        String hostName) throws SyncException {
        while (true) {
            try {
                uStoreClient.put(SyncStoreCCProvider.LOCAL_NODE_HOSTNAME,
                                 hostName);
                break;
            } catch (ObsoleteVersionException e) {}
        }
    }
    
    public static void setAuthInfo(IStoreClient<String, String> uStoreClient,
                                   AuthScheme authScheme,
                                   String keyStorePath,
                                   String keyStorePassword) 
                                           throws SyncException {
        while (true) {
            try {
                uStoreClient.put(SyncStoreCCProvider.AUTH_SCHEME, 
                                 authScheme.toString());
                uStoreClient.put(SyncStoreCCProvider.KEY_STORE_PATH, 
                                 keyStorePath);
                uStoreClient.put(SyncStoreCCProvider.KEY_STORE_PASSWORD, 
                                 keyStorePassword);
                break;
            } catch (ObsoleteVersionException e) {}
        }
    }

    public static void setSeeds(IStoreClient<String, 
                                             String> uStoreClient,
                                String seeds) 
            throws SyncException, IllegalArgumentException {
        String[] seedsStr = seeds.split(",");
        ArrayList<HostAndPort> seedsList = new ArrayList<HostAndPort>();
        for (String s : seedsStr) {
            try {
                seedsList.add(HostAndPort.fromString(s));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid seed: " + 
                                                   e.getMessage(), e);
            }
        }
        seeds = Joiner.on(',').join(seedsList);

        while (true) {
            try {
                uStoreClient.put(SyncStoreCCProvider.SEEDS, seeds);
                break;
            } catch (ObsoleteVersionException e) {}
        }
    }
    
    public static void reseed(IStoreClient<Short, ClusterNode> nodeStoreClient,
                              Short localNodeId) throws SyncException {
        while (true) {
            try {
                nodeStoreClient.delete(localNodeId);
                break;
            } catch (ObsoleteVersionException e) { };
        }
    }

    public static void setLocalDomainId(IStoreClient<Short, ClusterNode> nodeStoreClient,
                                        Short localNodeId,
                                        Short localDomainId)
                                                throws SyncException {
        while (true) {
            try {
                Versioned<ClusterNode> localNode = 
                        nodeStoreClient.get(localNodeId);
                if (localNode.getValue() == null) {
                    throw new SyncException("Could not set domain ID for local" + 
                                            " node because local node not " + 
                                            "found in system node store");
                }

                ClusterNode o = localNode.getValue();
                localNode.setValue(new ClusterNode(o.getHostname(),
                                                   o.getPort(),
                                                   o.getNodeId(),
                                                   localDomainId));
                nodeStoreClient.put(localNodeId, localNode);
                break;
            } catch (ObsoleteVersionException e) { };
        }

    }
    
    public static Short getLocalNodeId(IStoreClient<String, 
                                                    String> uStoreClient) 
            throws SyncException {
        String localNodeIdStr = 
                waitForValue(uStoreClient,
                             SyncStoreCCProvider.LOCAL_NODE_ID, 
                             10000000000L);
        if (localNodeIdStr == null) {
            return null;
        }
        return Short.valueOf(localNodeIdStr);
    }
    
    public static void deleteNode(IStoreClient<Short, ClusterNode> nodeStoreClient,
                                  short nodeId) throws SyncException {

        while (true) {
            try {
                nodeStoreClient.delete(nodeId);
                break;
            } catch (ObsoleteVersionException e) {}
        }
    }
    
    // **************
    // ClusterService
    // **************

    /**
     * Start the clustering service
     */
    public void run() {
        TimerTask task = new TimerTask() {
            
            @Override
            public void run(Timeout timeout) throws Exception {
                initTimeout();
            }
        };
        currentTimeout = syncManager.timer.newTimeout(task, 
                                                      ELECTION_TIMEOUT, 
                                                      TimeUnit.MILLISECONDS);
    }

    /**
     * If a node connects or disconnects from us, we should begin a 
     * new election
     */
    public void connectionStateChange() {
        try {
            logger.trace("connectionStateChange -> beginElection");
            beginElection();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * We received a leaderack message from another node
     * @throws InterruptedException 
     */
    public void leaderAck(short nodeId) throws InterruptedException {
        ClusterConfig cc = syncManager.getClusterConfig();

        switch (electionState) {
            case INIT:
            case FOLLOWER:
            case ELECTION:
                logger.trace("leaderAck({}) -> beginElection", electionState);
                beginElection();
                break;
            case LEADER_ELECT:
                boolean canInaugurate = false;
                synchronized (electionLock) {
                    currentSet.add(nodeId);
                    int numNodes = cc.getDomainNodes().size();
                    if (currentSet.size() >= (numNodes - 1)) {
                        // we've heard from everyone so no need to wait
                        canInaugurate = true;
                    }
                }
                if (canInaugurate) 
                    inaugurationTimeout();
                break;
            case LEADER:
                // Odd but OK
                break;
        }
    }

    /**
     * We received a leadercand message from another node
     * @throws InterruptedException 
     */
    @SuppressFBWarnings(value="SF_SWITCH_FALLTHROUGH")
    public void leaderCand(short candNodeId,  
                           int candWeight) throws InterruptedException {
        ClusterConfig cc = syncManager.getClusterConfig();

        switch (electionState) {
            case INIT:
            case LEADER_ELECT:
            case LEADER:
                logger.trace("leaderCand({}) -> beginElection", electionState);
                beginElection();
                // fall through
            case FOLLOWER:
                Short lid;
                synchronized (electionLock) {
                    lid = this.leaderNodeId;
                }
                if (lid == null || !lid.equals(leaderNodeId)) {
                    logger.trace("leaderCand(FOLLOWER) -> beginElection");
                    beginElection();
                }
                break;
            case ELECTION:
                boolean canDecide = false;
                synchronized (electionLock) {
                    currentSet.add(candNodeId);
                    if (candWeight > 0 &&
                        (topCandidate == null || 
                         candWeight > topCandidateWeight)) {
                        topCandidate = candNodeId;
                        topCandidateWeight = candWeight;
                    }
                    int numNodes = cc.getDomainNodes().size();
                    if (currentSet.size() >= (numNodes - 1)) {
                        // we've heard from everyone so no need to wait
                        canDecide = true;
                    }
                }
                if (canDecide)
                    decideElection();
                break;
        }
    }    

    /**
     * We received a leader message from another node
     * @throws InterruptedException 
     */
    public void leader(short leaderNodeId, 
                       int leaderWeight) throws InterruptedException {
        Short lid;
        switch (electionState) {
            case INIT:
                synchronized (electionLock) {
                    this.leaderNodeId = leaderNodeId;
                    enterState(ElectionState.FOLLOWER);
                    sendLeaderAck(leaderNodeId);
                    notifyFollower();
                }
                break;
            case ELECTION:
                Short top;
                synchronized (electionLock) {
                    top = topCandidate;
                    lid = leaderNodeId;
                    if (topCandidate == null || 
                        leaderWeight > topCandidateWeight) {
                        topCandidate = leaderNodeId;
                        topCandidateWeight = leaderWeight;
                    }
                }
                if (!Objects.equal(top, lid)) {
                    logger.trace("leader(ELECTION) -> beginElection");
                    beginElection();
                } else {
                    synchronized (electionLock) {
                        this.leaderNodeId = leaderNodeId;
                        enterState(ElectionState.FOLLOWER);
                    }
                    sendLeaderAck(leaderNodeId);
                }
                break;
            case FOLLOWER:
                synchronized (electionLock) {
                    lid = this.leaderNodeId;
                }
                if (lid == null || !lid.equals(leaderNodeId)) {
                    logger.trace("leader(FOLLOWER) -> beginElection");
                    beginElection();
                } else {
                    sendLeaderAck(leaderNodeId);
                }
                break;
            case LEADER_ELECT:
            case LEADER:
                beginElection();
                break;

        }
    }
    
    // *************
    // Local methods
    // *************
    
    private void enterState(ElectionState state) {
        synchronized (electionLock) {
            if (currentTimeout != null) {
                currentTimeout.cancel();
                currentTimeout = null;
            }
            currentSet.clear();
            electionState = state;
        }
    }

    /**
     * Called when the initial timeout period expires
     * @throws InterruptedException 
     */
    private void initTimeout() throws InterruptedException {
        if (electionState.equals(ElectionState.INIT)) {
            logger.trace("initTimeout -> beginElection");
            beginElection();
        }
    }

    /**
     * Called when the timeout period for a new election expires
     * @throws InterruptedException
     */
    private void electionTimeout() throws InterruptedException {
        if (electionState.equals(ElectionState.ELECTION)) {
            decideElection();
        }
    }

    /**
     * Called when the timeout period for a leader elect expires
     * @throws InterruptedException
     */
    private void inaugurationTimeout() throws InterruptedException {
        if (electionState.equals(ElectionState.LEADER_ELECT)) {
            Short oldLeader;
            synchronized (electionLock) {
                oldLeader = leaderNodeId;
                leaderNodeId = syncManager.getLocalNodeId();
                enterState(ElectionState.LEADER);
            }
            if (oldLeader == null || 
                !oldLeader.equals(syncManager.getLocalNodeId()))
                notifyLeader();
        }
    }
    
    @LogMessageDoc(level="INFO",
                   message="Entering election for local domain leader",
                   explanation="A leader is being elected for the local " +
                           "controller cluster.",
                   recommendation="This is normal unless you see it " + 
                           "repeatedly without convergence.  If no leader is" +
                           " elected, verify that all nodes can communicate.")
    private void beginElection() throws InterruptedException {
        if (electionState != ElectionState.ELECTION)
            logger.info("Entering election for local domain leader");

        ClusterConfig cc = syncManager.getClusterConfig();

        synchronized (electionLock) {
            if (cc.isLeaderAllowed()) {
                topCandidate = cc.getNode().getNodeId();
            } else {
                topCandidate = null;
            }
            topCandidateWeight = getNodeWeight();

            enterState(ElectionState.ELECTION);
        }
        
        Collection<ClusterNode> nodes = cc.getDomainNodes();
        for (ClusterNode node : nodes) {
            sendLeaderCand(node.getNodeId());
        }
        
        TimerTask task = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                electionTimeout();
            }
        };
        currentTimeout = syncManager.timer.newTimeout(task, 
                                                      ELECTION_TIMEOUT,
                                                      TimeUnit.MILLISECONDS);
    }

    private void decideElection() throws InterruptedException {
        ClusterConfig cc = syncManager.getClusterConfig();

        if (cc.isLeaderAllowed() &&
            (topCandidate == null || 
             cc.getNode().getNodeId() == topCandidate)) {
            // we won! Hooray for democracy!
            enterState(ElectionState.LEADER_ELECT);

            Collection<ClusterNode> nodes = cc.getDomainNodes();
            for (ClusterNode node : nodes) {
                sendLeader(node.getNodeId(), cc.getNode().getNodeId());
            }
            
            TimerTask task = new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                    inaugurationTimeout();
                }
            };
            currentTimeout = 
                    syncManager.timer.newTimeout(task, 
                                                 ELECTION_TIMEOUT,
                                                 TimeUnit.MILLISECONDS);
        } else {
            // we lost the election
            if (topCandidate == null) {
                // We don't like if if there's no leader, but if we don't
                // allow ourselves to become leader then we just have to 
                // live with it sometimes.
                if (cc.isLeaderAllowed()) {
                    logger.trace("decideElection -> beginElection");
                    beginElection();
                } else {
                    notifyFollower();
                }
            } else {
                enterState(ElectionState.FOLLOWER);
                Short oldLeader = leaderNodeId;
                leaderNodeId = topCandidate;
                if (oldLeader == null ||
                    (oldLeader.equals(cc.getNode().getNodeId()) &&
                     !topCandidate.equals(cc.getNode().getNodeId()))) {
                    notifyFollower();
                }
            }
        }
        
        riggedElection = false;
    }

    /**
     * Notify listeners that we are the new domain leader
     */
    private void notifyLeader() {
        logger.debug("Current node is now the domain leader");
        for (IClusterListener listener : listeners) {
            listener.notifyLeader();
        }
    }
 
    /**
     * Notify listeners that we are now a follower
     */
    private void notifyFollower() {
        logger.debug("Current node is now a domain follower; leader is {}",
                     leaderNodeId);
        for (IClusterListener listener : listeners) {
            listener.notifyFollower();
        }
    }
    
    private void sendLeader(short nodeId, 
                            short leaderNodeId) throws InterruptedException {
        ClusterConfig cc = syncManager.getClusterConfig();
        SyncMessage sm = new SyncMessage(MessageType.LEADER);
        AsyncMessageHeader h = new AsyncMessageHeader();
        h.setTransactionId(syncManager.rpcService.getTransactionId());
        LeaderMessage m = new LeaderMessage(h);
        m.setDomainId(cc.getNode().getDomainId());
        m.setLeaderNodeId(leaderNodeId);
        sm.setLeader(m);

        syncManager.rpcService.writeToNode(nodeId, sm);
    }
    
    private void sendLeaderCand(short nodeId) throws InterruptedException {
        ClusterConfig cc = syncManager.getClusterConfig();

        SyncMessage sm = new SyncMessage(MessageType.LEADER_CAND);
        AsyncMessageHeader h = new AsyncMessageHeader();
        h.setTransactionId(syncManager.rpcService.getTransactionId());
        LeaderCandMessage m = new LeaderCandMessage(h);
        m.setCandNodeId(cc.getNode().getNodeId());
        m.setDomainId(cc.getNode().getDomainId());
        m.setCandWeight(getNodeWeight());
        sm.setLeaderCand(m);
        
        syncManager.rpcService.writeToNode(nodeId, sm);
    }
   
    private void sendLeaderAck(short nodeId) throws InterruptedException {
        ClusterConfig cc = syncManager.getClusterConfig();
        SyncMessage sm = new SyncMessage(MessageType.LEADER_ACK);
        AsyncMessageHeader h = new AsyncMessageHeader();
        h.setTransactionId(syncManager.rpcService.getTransactionId());
        LeaderAckMessage m = new LeaderAckMessage(h);
        m.setDomainId(cc.getNode().getDomainId());
        m.setLeaderNodeId(nodeId);
        sm.setLeaderAck(m);
        
        syncManager.rpcService.writeToNode(nodeId, sm);
    }
    
    private int getNodeWeight() {
        ClusterConfig cc = syncManager.getClusterConfig();
        short nodeId = cc.getNode().getNodeId();
        int weight = nodeId;
        if (cc.isLeaderAllowed()) {
            // We stack the deck in favor of incumbents
            if (leaderNodeId != null && nodeId == leaderNodeId) {
                weight += Short.MAX_VALUE;
            }
            // In a rigged election, we want to ensure we win even over
            // established incumbents
            if (riggedElection) {
                weight += 2 * Short.MAX_VALUE;
            }
        } else {
            // If we don't want to be leader, we return a negative weight 
            weight = -nodeId;
        }
        return weight;
    }
    
    private Short getLocalNodeIdOrThrow() throws SyncException {
        Short localNodeId = getLocalNodeId(getUnsyncStore());
        if (localNodeId == null) 
            throw new SyncException("Could not retrieve node ID for local " + 
                                    "node; local node is not a cluster member");
        return localNodeId;
    }
    
    private IStoreClient<String, String> getUnsyncStore() 
            throws UnknownStoreException {
        return syncManager.getStoreClient(SyncStoreCCProvider.
                                          SYSTEM_UNSYNC_STORE, 
                                          String.class, String.class);
    }

    private IStoreClient<Short, ClusterNode> getNodeStore() 
            throws UnknownStoreException {
        return syncManager.getStoreClient(SyncStoreCCProvider.
                                          SYSTEM_NODE_STORE, 
                                          Short.class, ClusterNode.class);
    }
    
    private static String waitForValue(IStoreClient<String, 
                                                    String> uStoreClient,
                                       String key,
                                       long maxWait) throws SyncException {
        long start = System.nanoTime();
        while (start + maxWait > System.nanoTime()) {
            String v = uStoreClient.getValue(key);
            if (v != null) {
                return v;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * Represent the state for a node in the leader election protocol
     * @author readams
     */
    private enum ElectionState {
        /**
         * Inital state on startup
         */
        INIT,
        /**
         * Election in progress
         */
        ELECTION,
        /**
         * We've won the election but we're pausing to ensure that
         * we're the only node that has done so.
         */
        LEADER_ELECT,
        /**
         * We are currently the leader
         */
        LEADER,
        /**
         * We are currently a follower
         */
        FOLLOWER
    }
}
