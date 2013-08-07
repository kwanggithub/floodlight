package org.projectfloodlight.db.data.syncmem;

import org.projectfloodlight.db.BigDBException;

public class MutationOnSlaveException extends BigDBException {
    private static final long serialVersionUID = 1L;

    public MutationOnSlaveException() {
            super("Mutation on slave not allowed", BigDBException.Type.SEE_OTHER);
    }
}
