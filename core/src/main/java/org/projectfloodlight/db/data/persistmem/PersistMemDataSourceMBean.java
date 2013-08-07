package org.projectfloodlight.db.data.persistmem;

import java.io.File;

public interface PersistMemDataSourceMBean {

    public abstract long getBytesWritten();

    public abstract long getRealWrites();

    public abstract long getRequestedWrites();

    public abstract File getFile();

    public abstract long getNumExceptions();

    public abstract long getMsInWrite();

    public String getCurrentContent();
    public String getReadContent();
    public String getWrittenContent();
}
