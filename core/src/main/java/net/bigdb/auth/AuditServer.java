package net.bigdb.auth;

import java.util.Collections;

import net.bigdb.BigDBException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditServer {
    protected static Logger log = LoggerFactory.getLogger(AuditServer.class);
    private final Auditor defaultAuditor;
    private static AuditServer _instance = null;

    // XXX roth -- use a better Auditor.registry interface
    // to support unit testing this AuditServer
    private Auditor.Registry registry;

    /**
     * @return the one and only instance
     */
    protected synchronized static AuditServer getInstance() {
        if (_instance != null)
            return _instance;
        _instance = new AuditServer();
        return _instance;
    }

    /**
     * Enforces single instance, log to syslog by
     * default until initialized with a BigDB context
     * @throws Exception
     */
    private AuditServer()
    {
        defaultAuditor = new SyslogAuditor();
        registry = null;
    }

    public Auditor getDefaultAuditor() {
        return defaultAuditor;
    }

    public void setRegistry(Auditor.Registry registry) throws BigDBException {
        this.registry = registry;
    }

    private Iterable<Auditor> getAuditors() {

        if (registry == null)
            return Collections.singletonList(defaultAuditor);

        return registry.getAuditors();

    }

    public void commit(AuditEvent auditEvent) {
        for (Auditor auditor : getAuditors()) {
            // all reasonable implementations will disallow null auditors
            auditor.write(auditEvent);
        }
    }

}
