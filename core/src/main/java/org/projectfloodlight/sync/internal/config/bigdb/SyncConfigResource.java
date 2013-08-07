package org.projectfloodlight.sync.internal.config.bigdb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import javax.xml.bind.DatatypeConverter;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.FloodlightResource;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.annotation.BigDBInsert;
import org.projectfloodlight.db.data.annotation.BigDBParam;
import org.projectfloodlight.db.data.annotation.BigDBPath;
import org.projectfloodlight.db.data.annotation.BigDBProperty;
import org.projectfloodlight.db.data.annotation.BigDBQuery;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.sync.ClusterNode;
import org.projectfloodlight.sync.IClusterService;
import org.projectfloodlight.sync.internal.config.AuthScheme;
import org.projectfloodlight.sync.internal.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Resource for handling cluster configuration REST API calls
 * @author readams
 */
public class SyncConfigResource extends FloodlightResource {
    protected static final Logger logger =
            LoggerFactory.getLogger(SyncConfigResource.class.getName());
    
    @BigDBInsert
    @BigDBPath("config")
    public void setConfig(@BigDBParam("mutation-data") DataNode data)
            throws BigDBException {
        logger.debug("Updating cluster configuration");
        try {
            IClusterService clusterService = 
                    getModuleContext().getServiceImpl(IClusterService.class);

            DataNode localNodePort = data.getChild(Step.of("local-node-port"));
            DataNode localNodeIface = data.getChild(Step.of("local-node-iface"));
            DataNode localNodeHost = data.getChild(Step.of("local-node-host"));
            DataNode localNodeAuth = data.getChild(Step.of("local-node-auth"));
            DataNode localDomainId = data.getChild(Step.of("local-domain-id"));
            DataNode seeds = data.getChild(Step.of("seeds"));
            DataNode reseed = data.getChild(Step.of("reseed"));
            DataNode deleteNode = data.getChild(Step.of("delete-node"));
            DataNode newElection = data.getChild(Step.of("new-election"));

            if (!localNodeIface.isNull() && !localNodeIface.isValueNull()) {
                clusterService.setLocalNodeIface(localNodeIface.getString());
            }
            if (!localNodePort.isNull() && !localNodePort.isValueNull()) {
                clusterService.setLocalNodePort((int)localNodePort.getLong());
            }
            if (!localNodeHost.isNull() && !localNodeHost.isValueNull()) {
                clusterService.setLocalNodeHost(localNodeHost.getString());
            }
            if (!localNodeAuth.isNull()) {
                DataNode authScheme = 
                        localNodeAuth.getChild(Step.of("auth-scheme"));
                DataNode keyStorePath = 
                        localNodeAuth.getChild(Step.of("keystore-path"));
                DataNode keyStorePassword = 
                        localNodeAuth.getChild(Step.of("keystore-password"));

                String ksPath = 
                        keyStorePath.getString(clusterService.getKeystorePath());
                String ksPassword = 
                        keyStorePath.getString(clusterService.getKeystorePassword());
                
                if (!authScheme.isNull() && 
                    !keyStorePath.isNull() && 
                    !keyStorePassword.isNull()) {
                    AuthScheme as = 
                            AuthScheme.forValue(authScheme.getString("no-auth"));
                    clusterService.setAuthInfo(as, ksPath, ksPassword);
                }

                DataNode clusterSecret = 
                        localNodeAuth.getChild(Step.of("cluster-secret"));
                if (!clusterSecret.isNull()) {
                    String secretStr = clusterSecret.getString("");
                    byte[] secret;
                    if ("".equals(secretStr)) {
                        secret = CryptoUtil.secureRandom(16);
                    } else {
                        secret = DatatypeConverter.parseBase64Binary(secretStr);
                    }
                    
                    CryptoUtil.writeSharedSecret(ksPath, ksPassword, secret);
                }
            }
            if (!seeds.isNull()) {
                clusterService.setSeeds(seeds.getString(null));
            }
            if (!reseed.isNull() && reseed.getBoolean(false)) {
                clusterService.reseed();
            }
            if (!localDomainId.isNull() && !localDomainId.isValueNull()) {
                clusterService.setLocalDomainId((short)localDomainId.getLong());
            }
            if (!deleteNode.isNull() && !deleteNode.isValueNull()) {
                clusterService.deleteNode((short)deleteNode.getLong());
            }
            if (!newElection.isNull()) {
                boolean rigged = 
                        newElection.getChild(Step.of("rigged")).
                            getBoolean(false);
                clusterService.newElection(rigged);
            }
        } catch (BigDBException e) {
            throw e;
        } catch (Exception e) {
            throw new BigDBException(e.getMessage(), e);
        }
    }

    public static class LocalNodeAuth {
        String clusterSecret;

        @BigDBProperty("cluster-secret")
        public String getClusterSecret() {
            return clusterSecret;
        }
    }
    
    public static class Config {
        LocalNodeAuth localNodeAuth;

        @BigDBProperty("local-node-auth")
        public LocalNodeAuth getLocalNodeAuth() {
            return localNodeAuth;
        }
    }
    
    @BigDBQuery
    @BigDBPath("config")
    public Config readConfig() throws BigDBException {
        try {
            IClusterService clusterService = 
                    getModuleContext().getServiceImpl(IClusterService.class);

            Config config = new Config();
            LocalNodeAuth auth = new LocalNodeAuth();
            config.localNodeAuth = auth;
            String ksPath = clusterService.getKeystorePath();
            String ksPass = clusterService.getKeystorePassword();
            if (ksPath != null && ksPass != null) {
                byte[] secret = CryptoUtil.getSharedSecret(ksPath, ksPass);
                auth.clusterSecret = 
                        DatatypeConverter.printBase64Binary(secret);                
            }
            return config;
        } catch (BigDBException e) {
            throw e;
        } catch (Exception e) {
            throw new BigDBException(e.getMessage(), e);
        }
    }

    public static enum NodeStatus {
        DISCONNECTED,
        CONNECTED;
    }
    
    @SuppressFBWarnings(value="EQ_COMPARETO_USE_OBJECT_EQUALS")
    // Note: this class has a natural ordering that is inconsistent with equals.
    public static class Node implements Comparable<Node> {
        String hostname;
        int port;
        short nodeId;
        short domainId;
        NodeStatus status;

        public String getHostname() {
            return hostname;
        }
        public int getPort() {
            return port;
        }
        @BigDBProperty("node-id")
        public short getNodeId() {
            return nodeId;
        }
        @BigDBProperty("domain-id")
        public short getDomainId() {
            return domainId;
        }
        public NodeStatus getStatus() {
            return status;
        }
        
        @Override
        public int compareTo(Node o) {
            return Short.compare(nodeId, o.nodeId);
        }
    }
    
    @BigDBQuery
    @BigDBPath("status/nodes")
    public Collection<Node> getNodes() {
        IClusterService clusterService = 
                getModuleContext().getServiceImpl(IClusterService.class);
        ArrayList<Node> nodes = new ArrayList<>();
        short localNodeId = clusterService.getLocalNodeId();
        for (ClusterNode n : clusterService.getNodes()) {
            Node nn = new Node();
            nn.hostname = n.getHostname();
            nn.port = n.getPort();
            nn.nodeId = n.getNodeId();
            nn.domainId = n.getDomainId();
            nn.status = (nn.nodeId == localNodeId ||
                         clusterService.isConnected(n.getNodeId()))
                         ? NodeStatus.CONNECTED 
                         : NodeStatus.DISCONNECTED;
            nodes.add(nn);
        }
        Collections.sort(nodes);
        return nodes;
    }
    
    @BigDBQuery
    @BigDBPath("status/local-node-id")
    public short getLocalNodeId() {
        IClusterService clusterService = 
                getModuleContext().getServiceImpl(IClusterService.class);
        return clusterService.getLocalNodeId();
    }
    
    public static class LeaderNode {
        Short leaderId;
        
        @BigDBProperty("leader-id")
        public Short getLeaderId() {
            return leaderId;
        }
    }

    @BigDBQuery
    @BigDBPath("status/domain-leader")
    public LeaderNode getDomainLeader() {
        IClusterService clusterService = 
                getModuleContext().getServiceImpl(IClusterService.class);
        LeaderNode ln = new LeaderNode();
        ln.leaderId = clusterService.getDomainLeader();
        return ln;
    }
}
