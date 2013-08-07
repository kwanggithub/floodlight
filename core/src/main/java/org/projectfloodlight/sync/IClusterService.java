package org.projectfloodlight.sync;

import java.util.Collection;

import org.projectfloodlight.core.module.IFloodlightService;
import org.projectfloodlight.sync.error.SyncException;
import org.projectfloodlight.sync.internal.config.AuthScheme;

/**
 * Service interface provides information on cluster status and can perform
 * administrative tasks on the controller cluster
 * @author readams
 */
public interface IClusterService extends IFloodlightService {
    /**
     * Configure the authentication information
     * @param authScheme the authentication scheme to use
     * @param keyStorePath the path to the key store
     * @param keyStorePassword the password for accessing the key store
     * @throws SyncException
     */
    void setAuthInfo(AuthScheme authScheme, 
                     String keyStorePath,
                     String keyStorePassword) throws SyncException;
    
    /**
     * Set the domain ID for this cluster node
     * @param domainId the domain Id to set
     */
    public void setLocalDomainId(short domainId) throws SyncException;
    
    /**
     * Set the port to use for listening on the local node
     * @param port the TCP port on which to listen
     */
    public void setLocalNodePort(int port) throws SyncException;
    
    /**
     * Set the interface name from which we should derive our local cluster
     * IP address for cluster membership.
     * @param ifaceName the interface
     */
    public void setLocalNodeIface(String ifaceName) throws SyncException;
    
    /**
     * Set the hostname for the local node (overrides localNodeIface)
     * @param hostname the hostname/IP address to use for cluster membership
     */
    public void setLocalNodeHost(String hostname) throws SyncException;

    /**
     * Set the seeds list.  Note that this will only do something
     * if the node is not currently in a cluster 
     * @param seed Comma-separated list of seeds specified as 
     * ipaddr:port, such as 192.168.5.2:6642,192.168.6.2:6642
     */
    public void setSeeds(String seeds) throws SyncException;
    
    /**
     * If you simultaneously change the IP of every node in the cluster, the 
     * cluster may not automatically reform.  Run this command to cause it to 
     * rerun the bootstrap process while retaining existing node IDs.  The node 
     * will be put into its own local domain.  Note that this will only do
     * something useful if the seeds parameter is updated with at least one
     * cluster member.
     */
    public void reseed() throws SyncException;
    
    /**
     * Delete a node from the cluster.  Note that if the node is still 
     * active it will rejoin automatically, so only run this once the 
     * node has been disabled.
     * @param nodeId the node ID to remove
     */
    public void deleteNode(short nodeId) throws SyncException;
    
    /**
     * Get a collection of the nodes that are members of the cluster
     * @return the colleciton of {@link ClusterNode} objects
     */
    public Collection<ClusterNode> getNodes();

    /**
     * Get the node ID of the local node
     * @return the node ID
     */
    public short getLocalNodeId();

    /**
     * Get the domain leader for this local domain.  Can return null if there
     * is no leader
     * @return the domain leader node ID
     */
    public Short getDomainLeader();
    
    /**
     * Register a listener that will receive notifications about changes
     * to the leader state
     * @param listener the listener to register
     */
    public void registerListener(IClusterListener listener);

    /**
     * Start a new leader election.  Normally this will happen automatically, 
     * but you can force it to occur using this API call.
     * @param rigged If you specify a rigged election, then the node on which 
     * the election began will be the one to win, assuming it lives long enough 
     * to take office
     */
    public void newElection(boolean rigged);

    /**
     * Check whether the node is connected to the local node
     * @param nodeId the node to check
     * @return <code>true</code> if the node is connected
     */
    public boolean isConnected(short nodeId);

    /**
     * Get the path to the key store as configured for this node
     * @return the path
     */
    public String getKeystorePath();

    /**
     * Get the password to the key store as configured for this node
     * @return the password
     */
    public String getKeystorePassword();
}
