package org.projectfloodlight.db.yang;

import org.projectfloodlight.db.yang.Statement.Status;

public interface Statusable {
    public Status getStatus();
    public void setStatus(Status status);
}
