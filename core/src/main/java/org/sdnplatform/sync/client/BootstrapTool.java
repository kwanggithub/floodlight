package org.sdnplatform.sync.client;

import org.kohsuke.args4j.Option;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.ClusterNode;
import org.sdnplatform.sync.internal.ClusterManager;
import org.sdnplatform.sync.internal.config.SyncStoreCCProvider;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * This tool makes bootstrapping a cluster simpler by writing the appropriate
 * configuration parameters to the local system store 
 * @author readams
 */
public class BootstrapTool extends SyncClientBase {

    /**
     * Command-line settings
     */
    protected BootstrapToolSettings bSettings;

    protected static class BootstrapToolSettings 
        extends SyncClientBaseSettings {
        
        @Option(name="--seeds", aliases="-s",
                usage="Comma-separated list of seeds specified as " + 
                      "ipaddr:port, such as 192.168.5.2:6642,192.168.6.2:6642")
        protected String seeds;
        
        @Option(name="--localNodeDomain", aliases="-d",
                usage="Set the domain ID of the local node")
        protected short domainId;

        @Option(name="--localNodePort",
                usage="Set the listen port for the local node")
        protected int localNodePort;

        @Option(name="--localNodeIface",
                usage="Set the interface name to listen on for the local node")
        protected String localNodeIface;

        @Option(name="--localNodeHost",
                usage="Set the hostname for the local node (overrides " + 
                      "localNodeIface)")
        protected String localNodeHost;
        
        @Option(name="--reseed", aliases="-r",
                usage="If you simultaneously change the IP of every node " + 
                      "in the cluster, the cluster may not automatically " +
                      "reform.  Run this command to cause it to rerun the " +
                      "bootstrap process while retaining existing node IDs. " + 
                      "The node will be put into its own local domain.")
        protected boolean reseed;
        
        @Option(name="--delete", 
                usage="Remove the specified node from the cluster.  Note " +
                      "that if the node is still active it will rejoin " + 
                      "automatically, so only run this once the node has " + 
                      "been disabled.")
        protected short deleteNode;
    }

    public BootstrapTool(BootstrapToolSettings bootstrapSettings) {
        super(bootstrapSettings);
        this.bSettings = bootstrapSettings;
    }

    @SuppressFBWarnings(value="DM_EXIT")
    protected void bootstrap() throws Exception {
        IStoreClient<String, String> uStoreClient = 
                syncManager.getStoreClient(SyncStoreCCProvider.
                                           SYSTEM_UNSYNC_STORE, 
                                           String.class, String.class);
        IStoreClient<Short, ClusterNode> nodeStoreClient = 
                syncManager.getStoreClient(SyncStoreCCProvider.
                                           SYSTEM_NODE_STORE, 
                                           Short.class, ClusterNode.class);
        
        if (bSettings.localNodeIface != null) {
            ClusterManager.setLocalNodeIface(uStoreClient, 
                                             bSettings.localNodeIface);
        }
        if (bSettings.localNodePort != 0) {
            ClusterManager.setLocalNodePort(uStoreClient, 
                                            bSettings.localNodePort);
        }
        if (bSettings.localNodeHost != null) {
            ClusterManager.setLocalNodeIface(uStoreClient, 
                                             bSettings.localNodeHost);
        }
        if (bSettings.seeds != null) {
            ClusterManager.setAuthInfo(uStoreClient, 
                                       bSettings.authScheme, 
                                       bSettings.keyStorePath, 
                                       bSettings.keyStorePassword);
            ClusterManager.setSeeds(uStoreClient, bSettings.seeds);
        }
        Short localNodeId = null;
        if (bSettings.reseed || 
            bSettings.domainId != 0 || 
            bSettings.deleteNode != 0) {
            localNodeId = ClusterManager.getLocalNodeId(uStoreClient);
        }
        if (bSettings.reseed) {
            ClusterManager.reseed(nodeStoreClient, localNodeId);
        }
        if (bSettings.domainId != 0) {
            ClusterManager.setLocalDomainId(nodeStoreClient, 
                                            localNodeId, bSettings.domainId);
        }
        if (bSettings.deleteNode != 0) {
            if (bSettings.deleteNode == localNodeId) {
                err.println("You can't delete the current node from " + 
                            "the cluster");
                System.exit(5);
            }
            ClusterManager.deleteNode(nodeStoreClient, bSettings.deleteNode);
        }
    }
    
    // ****
    // Main 
    // ****

    public static void main(String[] args) throws Exception {
        BootstrapToolSettings settings = new BootstrapToolSettings();
        settings.init(args);
        BootstrapTool client = new BootstrapTool(settings);
        client.connect();
        try {
            client.bootstrap();
        } finally {
            client.cleanup();
        }
    }

}
