package org.projectfloodlight.db.auth;

/**
 * Interface contract for a module to write audit records.
 *
 * @author shudongz
 */
public interface Auditor {

    /**
     * Write out audit record for event to storage
     */
    public void write(AuditEvent event);

    public static interface Registry {
        /** retrieve an unordered list of valid (registered) method names */
        public Iterable<String> getAuditorNames();
        /** retrieve an ordered list of enabled (active) method names */
        public Iterable<Auditor> getAuditors();
    }

}
