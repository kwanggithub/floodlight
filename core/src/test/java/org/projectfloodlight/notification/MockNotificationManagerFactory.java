package org.projectfloodlight.notification;

import org.projectfloodlight.notification.INotificationManager;
import org.projectfloodlight.notification.INotificationManagerFactory;

public class MockNotificationManagerFactory implements
    INotificationManagerFactory {

    @Override
    public <T> INotificationManager getNotificationManager(Class<T> clazz) {
        return null;
    }

}
