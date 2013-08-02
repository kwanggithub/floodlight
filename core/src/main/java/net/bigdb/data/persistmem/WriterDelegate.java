package net.bigdb.data.persistmem;

import java.io.File;

import net.bigdb.data.DataNodeSerializationException;

/** a component that writes stuff */
interface WriterDelegate<T> {
    public void write(T root) throws DataNodeSerializationException;

    public void start();

    public void shutdown();

    public long getBytesWritten();

    public long getRealWrites();

    public long getRequestedWrites();

    public T getCurrentRequested();
    public T getLastWritten();

    public File getFile();

    public long getNumExceptions();

    public long getMsInWrite();
}