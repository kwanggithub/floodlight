package org.projectfloodlight.db.auth;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeSet;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.service.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BigDBAuditorRegistry implements Auditor.Registry {

    private final Service service;
    private final static Logger logger = LoggerFactory.getLogger(BigDBAuditorRegistry.class);
    private final ConcurrentHashMap<String, Auditor> auditors;

    public BigDBAuditorRegistry(Service service) {
        this.service = service;
        auditors = new ConcurrentHashMap<String, Auditor>();
    }

    public void registerAuditor(String name, Auditor auditor) {
        auditors.put(name, auditor);
    }

    /** retrieve the list of all <em>defined</em> auditors,
     * as opposed to the subset of those that are <em>enabled</em>.
     *
     * XXX roth -- does keySet() construct a collection on the spot,
     * or is smart enough to implement iteration in place?
     * If the former, then this can be a performance issue with
     * lots of accounting requests
     * See See BSC-3423.
     */
    @Override
    public Iterable<String> getAuditorNames() {
        return auditors.keySet();
    }

    /** Return an iterable of the <em>enabled</em> auditors
     * Note that this triggers a BigDB query for each accounting
     * record, which can be a performance issue.
     * See BSC-3423.
     */
    @Override
    public Iterable<Auditor> getAuditors() {

        ArrayList<Auditor> validMethods = new ArrayList<Auditor>();

        try {
            Query query = Query.parse("/core/aaa/accounting");
            DataNodeSet dataNodeSet =
                    service.getTreespace("controller").queryData(query,
                            AuthContext.SYSTEM);
            for (DataNode node : dataNodeSet) {
                DataNode nameNode = node.getChild("name");
                if (nameNode.isNull()) continue;
                String method = nameNode.getString();
                logger.debug("found configured method: {}", method);
                if (auditors.containsKey(method)) {
                    Auditor a = auditors.get(method);
                    // unlike vanilla HashMap, null is not supported
                    validMethods.add(a);
                }
            }
            return validMethods;
        } catch (BigDBException e) {
            logger.error("failed to query BigDB", e);
        }

        return validMethods;
    }
}
