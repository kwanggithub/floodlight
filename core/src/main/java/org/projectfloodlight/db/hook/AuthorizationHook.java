package org.projectfloodlight.db.hook;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.BigDBInternalError;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.schema.SchemaNode;

/** An authorization hook makes the decision about whether a given user (as
 * indicated by an AuthContext) is authorized to make a given change.
 *
 * <h1>Workflow</h1>
 * <ul>
 *   <li>On <b>mutations</b>, registered AuthorizationHooks are currently invoked before commit time,
 * if they are attached to subtrees that changed as part of the commit. (Note: this is planned to
 * change)
 *   <li>On <b>queries</b> authorization hooks are invoked before the query results are returned.
 * </ul>
 *
 * <h1>State that can be inspected:</h1>
 * The AuthorizatinoHook is free to look at the AuthContext to determine the
 * user permission, and to look at the old and new values of the proposed transaction.
 *
 * <h1>State that may be changed:</h1>
 * The AuthorizationHook <b>MUST NOT</b> make any state changes.
 *
 * <h1>Warnings:</h1>
 * <strong>Warning: PENDING REFACTORING:</strong> The current design of
 * Authorization Hook has been determined to be insufficient. There is work
 * going on to refactor AuthorizationHooks to be invoked
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public interface AuthorizationHook {

    public enum Decision {
        ACCEPT,
        REJECT,
        UNDECIDED
    };

    public enum Operation {
        QUERY,
        MUTATION
    }

    public enum Stage {
        PREAUTHORIZATION,
        AUTHORIZATION,
    }

    /**
     * Result class returned from the authorize method. This includes the
     * decision about whether the data was accepted, rejected, or undecided as
     * well as an optional message that can be used with a REJECT decision to
     * indicate what triggered the REJECT decision.
     */
    public static class Result {

        /** Standard accept result with empty message */
        public static final Result ACCEPT = new Result(Decision.ACCEPT);
        /** Standard reject result with empty message */
        public static final Result REJECT = new Result(Decision.REJECT);
        /** Standard undecided result with empty message */
        public static final Result UNDECIDED = new Result(Decision.UNDECIDED);

        private final Decision decision;
        private final String reason;

        public Result(Decision decision) {
            this(decision, "");
        }

        public Result(Decision decision, String reason) {
            assert decision != null;
            assert reason != null;
            this.decision = decision;
            this.reason = reason;
        }

        public Decision getDecision() {
            return decision;
        }

        public String getReason() {
            return reason;
        }

        public static Result of(Decision decision) {
            switch (decision) {
            case ACCEPT:
                return AuthorizationHook.Result.ACCEPT;
            case REJECT:
                return AuthorizationHook.Result.REJECT;
            case UNDECIDED:
                return AuthorizationHook.Result.UNDECIDED;
            default:
                throw new BigDBInternalError("Unhandled authorization hook decision");
            }
        }

        @Override
        public String toString() {
            return reason.isEmpty() ? decision.toString() :
                String.format("%s (Reason: %s)", decision.toString(), reason);
        }
    }

    public interface Context {
        /**
         * @return the operation that the hook is authorizing
         */
        public Operation getOperation();

        /**
         * @return the stage at which the authorization hook is invoked.
         */
        public Stage getStage();

        /**
         * @return the schema node where the hook is registered
         */
        public SchemaNode getHookSchemaNode();

        /**
         * Returns the location path of the data node corresponding to where
         * the hook is registered. The location path is fully qualified, i.e.
         * all list nodes in the hook path have predicates that specify all of
         * the key values for the list, so a single list element is specified.
         *
         * @return Fully qualified location path to where the hook is registered
         */
        public LocationPathExpression getHookPath();

        /**
         * Returns the data node where the hook is registered with the old
         * contents it had before the mutations in the new, proposed candidate
         * tree.
         *
         * @return Old hook data node
         */
        public DataNode getOldHookDataNode();

        /**
         * Returns the data node where the hook is registered with the contents
         * from the new, proposed candidate tree.
         *
         * @return New hook data node instance in the new proposed candidate tree
         */
        public DataNode getNewHookDataNode();

        /**
         * Return a data node representing the nodes that were written in the
         * new, mutated candidate hook data node. In this case "written" means
         * that the mutation operation applied a write/mutation to the data
         * node, even if the value didn't change. So the mutated data node is a
         * superset of the diffs between the old and new nodes. Deletions of
         * data nodes in the old tree are represented by the special data node
         * value of DataNode.DELETED, which is an instance of a NullDataNode,
         * i.e. isNull() returns true.
         *
         * @return Data node representing mutated nodes in the new, proposed
         *         candidate hook data node.
         */
        public DataNode getWrittenHookDataNode();

        /**
         * @return Old root data node of the tree before the mutations in
         * the new, proposed candidate tree.
         */
        public DataNode getOldRootDataNode();

        /**
         * @return New root data node instance after the mutations in the new
         * proposed candidate tree
         */
        public DataNode getNewRootDataNode();

        /**
         * Return a data node representing the nodes that were written in the
         * new, mutated candidate root data node. In this case "written" means
         * that the mutation operation applied a write/mutation to the data
         * node, even if the value didn't change. So the mutated data node is a
         * superset of the diffs between the old and new nodes. Deletions of
         * data nodes in the old tree are represented by the special data node
         * value of DataNode.DELETED, which is an instance of a NullDataNode,
         * i.e. isNull() returns true.
         *
         * @return Data node representing mutated nodes in the new, proposed
         *         candidate root data node.
         */
        public DataNode getWrittenRootDataNode();

        /**
         * @return Auth context used to access session and user info.
         */
        public AuthContext getAuthContext();
    }

    /**
     * Authorize the requested operation/commit.
     *
     * @param context
     * @return
     * @throws BigDBException
     */
    public Result authorize(Context context) throws BigDBException;
}
