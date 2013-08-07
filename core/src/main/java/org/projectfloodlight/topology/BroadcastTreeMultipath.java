package org.projectfloodlight.topology;

import java.util.ArrayList;
import java.util.HashMap;

import org.openflow.util.HexString;
import org.projectfloodlight.routing.Link;

public class BroadcastTreeMultipath {
    protected HashMap<Long, ArrayList<Link>> links;
    protected HashMap<Long, Integer> costs;

    public BroadcastTreeMultipath() {
        links = new HashMap<Long, ArrayList<Link>>();
        costs = new HashMap<Long, Integer>();
    }

    public BroadcastTreeMultipath(HashMap<Long, ArrayList<Link>> links, HashMap<Long, Integer> costs) {
        this.links = links;
        this.costs = costs;
    }

    // deprecate?
    // legacy method - change to getTreeLinks or now return first element
    public Link getTreeLink(long node) {
        return links.get(node).get(0);
    }

    public int getCost(long node) {
        if (costs.get(node) == null)
            return -1;
        return (costs.get(node));
    }

    public HashMap<Long, ArrayList<Link>> getLinks() {
        return links;
    }

    public ArrayList<Link> getLinks(long node) {
        return links.get(node);
    }
    
    // deprecate?
    // mynode is src of link in dst rooted tree convention
    public void addTreeLink(long myNode, Link link) {
        links.get(myNode).add(link);
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (long n : links.keySet()) {
            sb.append("[" + HexString.toHexString(n) + ": cost=" + costs.get(n)
                    + ", " + links.get(n) + "]");
        }
        return sb.toString();
    }

    public HashMap<Long, Integer> getCosts() {
        return costs;
    }
}
