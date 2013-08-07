package org.projectfloodlight.db.auth.application;

import org.restlet.Request;

public interface ApplicationRegistry extends Iterable<ApplicationRegistration> {

    // public abstract Collection<ApplicationRegistration> getRegistry();

    public abstract void registerApplication(ApplicationRegistration app);

    /** determine if this application key is correct */
    public abstract ApplicationContext getApplication(Request request);

}
