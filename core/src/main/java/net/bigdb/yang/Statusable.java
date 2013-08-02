package net.bigdb.yang;

import net.bigdb.yang.Statement.Status;

public interface Statusable {
    public Status getStatus();
    public void setStatus(Status status);
}
