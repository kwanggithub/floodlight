package net.bigdb.data;

import java.util.Set;

import net.bigdb.BigDBException;
import net.bigdb.auth.AuthContext;
import net.bigdb.query.Query;

/**
 * Listener interface for receiving notifications when the treespace is mutated.
 * 
 * !!!!! THIS API IS DEPRECATED. !!!!!
 * Use the WatchHook mechanism instead.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public interface MutationListener {

    public enum Operation { MODIFY, DELETE }
    
    /**
     * This is called when nodes are mutated, either modified (inserted or
     * updated) or deleted.
     * FIXME: The argument to this should really be an XPath (or really 
     * in this case more like an XPointer), not a Query.
     * Probably need to have a separate XPath class distinct from the Query
     * class that contains the steps/predicates but not the sort order
     * 
     * @param paths
     */
    public void dataNodesMutated(Set<Query> mutatedNodes, Operation operation,
            AuthContext authContext) throws BigDBException;
}

