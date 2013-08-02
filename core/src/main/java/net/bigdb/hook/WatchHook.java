package net.bigdb.hook;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.schema.SchemaNode;

/**
 * A watch hook is registered to watch for mutations in a BigDB subtree. WatchHooks
 * are intended to update Operational State that is influenced by BigDB content.
 * If a watch hook is registered for a list node, then it is invoked once for
 * each list element that has changed.
 *
 * <h1>Workflow</h1>
 * Watch hooks are executed <b>after</b> a new BigDB tree is committed.
 *
 * <h1>State that can be expected</h1>:
 * The WatchHook is free to inspect the entire transaction, as encompassed by the
 * old, new and diff trees, and operational state to determine which changes it needs to make.
 *
 * <h1>State that can be modified:</h1>:
 * Since the current transaction is already commited, the WatchHook cannot modify it any more.
 * It is free to modify operational state as needed.  You may be able to run a new BigDB transaction
 * from the WatchHook (but see not):
 *
 * <h1>Warning: Running a new bigdb transaction from the watchhook</h1:
 * Conceptually, it is possible to schedule a <b>new</b> bigdb transactions from the WatchHook,
 * but this is tricky (need to take care to avoid circular dependencies), and has not been tested.
 * Talk to RobV/AndiW before implementing.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public interface WatchHook {

    /**
     * The context contains all of the state available to the hook.
     */
    public interface Context {
        /**
         * Returns the location path of the data node corresponding to where
         * the hook is registered. The location path is fully qualified.
         * All list nodes in the hook path have predicates that specify all of
         * the key values for the list, so a single list element is specified.
         *
         * @return Fully qualified location path to where the hook is registered
         */
        public LocationPathExpression getHookPath();

        /**
         * @return the schema node where the hook is registered
         */
        public SchemaNode getHookSchemaNode();

        /**
         * Returns the data node where the hook is registered with the old
         * contents it had before the commit of the mutated tree.
         *
         * @return Old hook data node instance
         */
        public DataNode getOldHookDataNode();

        /**
         * Returns the data node where the hook is registered with the new
         * contents it has after the commit of the mutated tree.
         *
         * @return New hook data node instance
         */
        public DataNode getNewHookDataNode();

        /**
         * Return a data node representing the differences between the old
         * contents of the node where the hook is registered and the new,
         * mutated contents. The data node only contains child data nodes for
         * things that changed. Deletions of data nodes in the old tree are
         * represented by NULL data nodes.
         *
         * @return New, mutated hook data node
         */
        public DataNode getHookDataNodeDiffs();

        /**
         * Returns the old root data node of the tree before the commit of the
         * mutated tree.
         *
         * @return Old root data node
         */
        public DataNode getOldRootDataNode();

        /**
         * Returns the new root data node of the tree after the commit of the
         * mutated tree
         * @return New, mutated root data node
         */
        public DataNode getNewRootDataNode();

        /**
         * Return a data node representing the differences between the old
         * root data node of the tree and the new, mutated/committed root
         * data node. The data node only contains child data nodes for
         * things that changed, either inserted or updated.
         * Deletions of data nodes in the old tree are represented by the
         * special data node value of DataNode.DELETED, which is an instance
         * of a NullDataNode, i.e. isNull() returns true.
         *
         * @return Data node representing diffs between the old and new roots
         */
        public DataNode getRootDataNodeDiffs();
    }

    /**
     * Respond to the mutation of the data. For list nodes this is called once
     * for each list element that has been mutated.
     *
     * @param context
     */
    public void watch(Context context) throws BigDBException;
}
