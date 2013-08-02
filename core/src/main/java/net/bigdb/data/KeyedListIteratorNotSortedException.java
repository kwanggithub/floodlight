package net.bigdb.data;

import net.bigdb.BigDBInternalError;

public class KeyedListIteratorNotSortedException extends BigDBInternalError {

    private static final long serialVersionUID = 1L;

    private final IndexValue previousKeyValue;
    private final IndexValue currentKeyValue;

    public KeyedListIteratorNotSortedException(IndexValue previousKeyValue,
            IndexValue currentKeyValue) {
        super(String.format(
                "Iterator for a keyed list node must return the entries " +
                "sorted by key value; previous key: \"%s\"; " +
                "current key: \"%s\"", previousKeyValue, currentKeyValue));
        this.previousKeyValue = previousKeyValue;
        this.currentKeyValue = currentKeyValue;
    }

    public IndexValue getPreviousKeyValue() {
        return previousKeyValue;
    }

    public IndexValue getCurrentKeyValue() {
        return currentKeyValue;
    }
}
