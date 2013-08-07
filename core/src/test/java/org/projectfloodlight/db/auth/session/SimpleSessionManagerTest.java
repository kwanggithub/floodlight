package org.projectfloodlight.db.auth.session;

import org.projectfloodlight.db.auth.session.SimpleSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Ticker;

public class SimpleSessionManagerTest extends AbstractSessionManagerTest<SimpleSessionManager> {
    protected static Logger logger =
        LoggerFactory.getLogger(SimpleSessionManagerTest.class);

    @Override
    public SimpleSessionManager getSessionManager(String cacheSpec, Ticker timeSource)
            throws Exception {
        return new SimpleSessionManager(cacheSpec, timeSource);
    }


}
