package org.sdnplatform.sync;

/**
 * Interface for a
 * @author readams
 */
public interface IClusterListener {
    /**
     * The current node is now the domain leader
     */
    public void notifyLeader();
    
    /**
     * The current node is now a domain follower
     */
    public void notifyFollower();
}
