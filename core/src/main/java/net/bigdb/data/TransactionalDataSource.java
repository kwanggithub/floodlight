package net.bigdb.data;

import net.bigdb.BigDBException;

/**
 * Interface for a data source that supports transactional semantics. This means
 * that it supports atomic updates of the root of the data node tree.
 *
 * FIXME: This is possibly/probably a transitional solution to supporting
 * transactional semantics in BigDB. Longer-term the commit semantics should
 * be handled at the treespace level.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public interface TransactionalDataSource extends DataSource {

    /**
     * Listener interface for receiving notifications before a new root data
     * node is committed for the data source. If the listener throws an
     * exception the commit is rejected and the exception is propagated to the
     * caller.
     *
     * @param oldRootDataNode
     *            the root tree before the proposed commit
     * @param newRootDataNode
     *            the new root tree after the proposed commit
     * @param rootDataNodeDiffs
     *            the diffs from the old root to the new root
     * @throws BigDBException
     */
    public interface PreCommitListener {
        public void preCommit(DataNode oldRootDataNode,
                DataNode newRootDataNode, DataNode rootDataNodeDiffs)
                        throws BigDBException;
    }

    /**
     * Listener interface for receiving notifications after a new root data node
     * is committed for the data source.
     *
     * @param oldRootDataNode
     *            the root tree before the commit
     * @param newRootDataNode
     *            the new root tree after the commit
     * @param rootDataNodeDiffs
     *            the diffs from the old root to the new root
     * @throws BigDBException
     */
    public interface PostCommitListener {
        public void postCommit(DataNode oldRootDataNode,
                DataNode newRootDataNode, DataNode rootDataNodeDiffs)
                        throws BigDBException;
    }

    /**
     * Add a listener that is notified before a new root is committed.
     *
     * @param listener
     */
    public void addPreCommitListener(PreCommitListener listener);

    /**
     * Add a listener that is notified after a new root is committed.
     *
     * @param listener
     */
    public void addPostCommitListener(PostCommitListener listener);
}
