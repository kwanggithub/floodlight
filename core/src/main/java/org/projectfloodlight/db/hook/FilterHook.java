package org.projectfloodlight.db.hook;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.expression.LocationPathExpression;

/**
 * A filter hook is registered for a list node and is used to filter out some of
 * the list elements from being processed in a query or mutation operation.
 * Typically this filtering process is driven by the groups/permissions of the
 * current user that determine the subset of the list elements that are visible.
 * The filter hook is called once for each list element that matches the
 * predicates (if any) for the list node in the query path. If the filter hook
 * excludes the list element then it is not included in the query result set
 * (for queries) and mutations are not applied to it (for mutation operations).
 *
 * <h1>Workflow<h1>:
 * The FilterHook is invoked as the tree is first inspected. For all subsequent operations
 * (query, mutation etc.), nodes that are being EXCLUDED are treated as non-existing.
 *
 * <h1>State that can be inspected:</h1>
 * The FilterHook is free to inspect the entire current (old) state of the database, as well
 * as current operational state.
 *
 * <h1>State that may be changed:</h1>
 * The FilterHook <b>MUST NOT</b> make any state changes, neither to BigDB nor to the OpState.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public interface FilterHook {

    public enum Result {
        /** Exclude this list element from further processing */
        EXCLUDE,
        /** Include this list element in further processing */
        INCLUDE
    };

    /** What operation is being performed? */
    public enum Operation {
        /** The data is being queried */
        QUERY,
        /** The data is being mutated */
        MUTATION
    }

    public interface Context {
        /**
         * @return The operation being performed.
         */
        public Operation getOperation();

        /**
         * Returns the location path of the data node corresponding to where the
         * hook is registered. The location path is fully qualified. All list
         * nodes in the hook path have predicates that specify all of the key
         * values for the list, so a single list element is specified.
         *
         * @return Fully qualified location path to where the hook is registered
         */
        public LocationPathExpression getHookPath();

        /**
         * Returns the data node where the hook is registered. When the filter
         * hook is called for a mutation operation this data node has the
         * contents of the data before applying the mutation.
         *
         * @return Old hook data node instance
         */
        public DataNode getHookDataNode();

        /**
         * @return Auth context used to access session and user info.
         */
        public AuthContext getAuthContext();
    }

    /**
     * Execute the filter hook and return a result to indicate if the list
     * element should be included or excluded. The hooks typically examines the
     * node returned from the getHookDataNode method of the context to decide
     * whether to include or exclude.
     *
     * @param context
     * @return INCLUDE or EXCLUDE decision
     * @throws BigDBException
     */
    public Result filter(Context context) throws BigDBException;
}
