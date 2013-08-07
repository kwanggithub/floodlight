package org.projectfloodlight.db.auth.application;

import org.projectfloodlight.db.auth.AuditEvent;

/** represent an application requesting global access.
 *
 * TODO possibly include additional context to make this
 * more session-like
 *
 * @author Carl Roth <carl.roth@bigswitch.com>
 *
 */
public class ApplicationContext {

    public static final String APPLICATION_AVPAIR = "application_id";

    private final String name;

    public ApplicationContext(String n) {
        name = n;
    }

    public String getName() {
        return name;
    }

    public AuditEvent.Builder eventBuilder() {
        AuditEvent.Builder b = new AuditEvent.Builder();
        b.avPair(APPLICATION_AVPAIR, getName());
        return b;
    }

}
