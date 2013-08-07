package org.projectfloodlight.linkdiscovery;

import org.projectfloodlight.linkdiscovery.LinkDiscoveryManager;
import org.projectfloodlight.linkdiscovery.LinkInfo;
import org.projectfloodlight.routing.Link;

/**
 * A LinkDiscoveryManager specifically for unit tests.
 * @author alexreimers
 */
public class LinkDiscoveryManagerTestClass extends LinkDiscoveryManager {
    /**
     * A public method to add a link to the topology. This calls the method
     * in the parent.
     */
    public boolean addOrUpdateLink(Link lt, LinkInfo newInfo) {
        return super.addOrUpdateLink(lt, newInfo);
    }

}
