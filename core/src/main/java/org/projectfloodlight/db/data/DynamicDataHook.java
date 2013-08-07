package org.projectfloodlight.db.data;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Query;

/**
 * Hook interface for applications to populate subtrees of the BigDB data tree
 * with with data backed by objects managed by the application, i.e. operational
 * state.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public interface DynamicDataHook {

    /**
     * The operation being performed that triggered the call to the dynamic
     * data hook. These correspond to the top-level treespace operations.
     */
    public enum Operation { QUERY, INSERT, REPLACE, UPDATE, DELETE };

    /**
     * The context passed to the dynamic data hook that contains the info
     * needed by the application to service the current operation.
     */
    public interface Context {

        /** @return the operation being performed */
        public Operation getOperation();

        /**
         * @return the path where the dynamic data hook is registered.
         * All steps in the path before the final step are fully qualified,
         * i.e. steps for keyed list nodes have predicates for each of the keys
         * for the list and unkeyed list nodes have an index predicate.
         * The final step contains the predicates as specified in the operation
         * from the client. The hook code can use the predicate if it needs to
         * (or to optimize performance), but in most case the hook code will
         * ignore the predicates and let the core code handle evaluation of the
         * predicates to filter the results.
         */
        public LocationPathExpression getHookPath();

        /** @return the original query specified in the request from the client */
        public Query getQuery();

        /** @return the input data for update/replace mutation operations */
        public DataNode getMutationDataNode();

        /** @return the authentication context for the current operation */
        public AuthContext getAuthContext();

        /**
         * Request properties support sharing of state across multiple
         * invocations of dynamic data hooks servicing a single client
         * request. getRequestProperty returns a property value that was set
         * previously by another dynamic data hook.
         *
         * @return application-defined property value
         */
        public Object getRequestProperty(String name);

        /**
         * Request properties support sharing of state across multiple
         * invocations of dynamic data hooks servicing a single client request.
         * setRequestProperty sets a property value that becomes available to
         * subsequent dynamic data hooks that are called during processing of
         * the current client request.
         *
         * @param name name of the property.
         * @param value value of the property
         */
        public void setRequestProperty(String name, Object value);
    }

    /**
     * Execute the dynamic data hook.
     *
     * @param context
     *            the context the hook can query to get information about the
     *            operation being performed.
     * @return an application object to be serialized to a BigDB data node
     */
    public Object doDynamicData(Context context) throws BigDBException;
}
