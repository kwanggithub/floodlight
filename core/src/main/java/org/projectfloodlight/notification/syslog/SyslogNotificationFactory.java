package org.projectfloodlight.notification.syslog;

import org.projectfloodlight.notification.INotificationManager;
import org.projectfloodlight.notification.INotificationManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyslogNotificationFactory implements
    INotificationManagerFactory {

    @Override
    public <T> INotificationManager getNotificationManager(Class<T> clazz) {
        Logger logger = LoggerFactory.getLogger(clazz.getCanonicalName() + ".syslog.notification");
        return new SyslogNotificationManager(logger);
    }

}
