package net.bigdb.data.syncmem;

import net.bigdb.BigDBException;

public class MutationOnSlaveException extends BigDBException {
    private static final long serialVersionUID = 1L;

    public MutationOnSlaveException() {
            super("Mutation on slave not allowed", BigDBException.Type.SEE_OTHER);
    }
}
