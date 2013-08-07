package org.projectfloodlight.notification.syslog;

import org.projectfloodlight.notification.INotificationManager;
import org.slf4j.Logger;

public class SyslogNotificationManager implements INotificationManager {

    private final Logger logger;

    public SyslogNotificationManager(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void postNotification(String notes) {
        logger.warn(notes);
    }

}
