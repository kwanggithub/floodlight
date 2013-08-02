package net.bigdb.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyslogAuditor implements Auditor {

    protected final static Logger log = LoggerFactory.getLogger(AuditServer.class);

    /**
     * Default auditor implementation via syslog
     */
    @Override
    public void write(AuditEvent event) {
        StringBuilder b = new StringBuilder();
        b.append("AUDIT EVENT: ");
        b.append(event.getType());
        for (String attr:event.getAvPairs().keySet()) {
            b.append(" ");
            b.append(attr);
            b.append("=");
            b.append(event.getAvPairs().get(attr));
        }
        // TODO extend logback to add log.audit()?
        log.info(b.toString());
    }

}
