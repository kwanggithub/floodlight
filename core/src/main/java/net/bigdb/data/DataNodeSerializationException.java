package net.bigdb.data;

import net.bigdb.BigDBException;

public class DataNodeSerializationException extends BigDBException {

    private static final long serialVersionUID = 7076791294192553710L;

    private static final String EXCEPTION_MESSAGE =
            "Error serializing data node to JSON";

    public DataNodeSerializationException() {
        super(EXCEPTION_MESSAGE);
    }

    public DataNodeSerializationException(Throwable exc) {
        super(EXCEPTION_MESSAGE, exc);
    }
}
