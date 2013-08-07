package org.projectfloodlight.db.hook;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.expression.LocationPathExpression;

/**
 * A validation hook enforces application-specific validation/integrity
 * constraints on the database <b>state</b> before it is commited.
 * This is intended for validations that cannot be represented by the validation
 * primitives in YANG. This could involve custom validation of individual
 * leaf fields or validation that requires checks across multiple fields in a
 * container or list element.
 *
 * <h1>Workflow</h1>
 * Validation hooks are consulted directly before the BigDB commit takes place. They
 * check the <b>NEW</b> database state for integrity / consistency, and return
 * their decision. If any attached validation hook returns invalid, the transaction
 * is cancelled.
 *
 * <h1>Context the hook may inspect</h1>:
 * Validation hooks are called after all individual modifications of a transaction
 * are applied. As such, they are free to look at the <b>entire new (proposed) database tree</b>
 * to check for consistency. A Validation shook should <b>not</b> inspect the 'old' state of
 * the database, nor should it use cached Operational State that is defined off the old operational
 * database state.
 *
 * <h1>State the hook can modify:</h1>
 * A Validation Hook must <b>NEVER</b> modify state, neither on the bigdb configuration side, nor on
 * the operational side. Other hooks may still abort the transaction.
 *
 * <b>Note:</b>
 * A BigDB transaction is defined as an atomic switch between two arbitrary valid configurations c and c'
 * that the controller operates in. As such, the ValidationHook should inspect the NEW database
 * state for consistency. It should NOT consider elements of the old state, or items
 * cached from the currently running operational state. Otherwise, the Validation hook
 * may erroeously fail larger transactions c->c' that can be issued, e.g., by the HA synchronization,
 * Orchestration Tools, or an atomic configuration replacement by the CLI.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public interface ValidationHook {

    /**
     * The decision included in the result about whether the data was valid
     * or invalid.
     */
    public enum Decision {
        VALID,
        INVALID
    };

    /**
     * Result class returned from the validate method. This includes the
     * decision about whether the data was valid or invalid as well as an
     * optional message that can be used with an INVALID decision to indicate
     * what triggered the INVALID decision.
     */
    public static final class Result {

        /** Standard valid result with empty message */
        public static final Result VALID = new Result(Decision.VALID);
        /** Standard invalid result with empty message */
        public static final Result INVALID = new Result(Decision.INVALID);

        private final Decision decision;
        private final String message;

        public Result(Decision decision) {
            this(decision, "");
        }

        public Result(Decision decision, String message) {
            assert decision != null;
            assert message != null;
            this.decision = decision;
            this.message = message;
        }

        public Decision getDecision() {
            return decision;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "Result [decision=" + decision + ", message=" + message + "]";
        }

    }

    public interface Context {
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
         * If the validation is registered with a path that specifies a list
         * node, this method returns true if the hook was registered to
         * validate the overall list as opposed to validating the individual
         * list elements. If the hook is registered with a non-list node, then
         * this returns false.
         *
         * @return true if validating the list; otherwise returns false
         */
        public boolean isListValidation();

        /**
         * Returns the data node where the hook is registered with the contents
         * from the new, proposed candidate tree.
         *
         * @return Hook data node instance in the new proposed candidate tree
         */
        public DataNode getHookDataNode();

        /**
         * @return Root data node instance after the mutations in the new
         *         proposed candidate tree
         */
        public DataNode getRootDataNode();
    }

    /**
     * Validate the contents of the data node where the hook is installed. The
     * data node to be validated is obtained using the getHookDataNode method
     * from the context.
     *
     * @param context
     * @return result with VALID or INVALID decision
     */
    public Result validate(Context context) throws BigDBException;
}
