package org.projectfloodlight.db.data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Set;

import javax.annotation.CheckForNull;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.schema.SchemaNode;

/**
 *  Represents a data node in the BigDB tree.
 *  Exposes a "fat" interface for convenient access to node attributes.
 *  Implements the Iterable interface for convenient access to child nodes.
 *
 *  Methods in this class avoid returning null where possible and return
 *  sensible empty objects instead.
 *
 *  Messages that are fundamentally unsupported by the receiving type raise an
 *  UnsupportedOperationException (e.g., asking a scalar value of a container
 *  or trying to iterate over a leaf).
 *
 * @author Rob Vaterlaus <rob.vaterlaus@bigswitch.com>
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public interface DataNode extends Iterable<DataNode> {

    public enum NodeType {
        NULL,
        LEAF,
        CONTAINER,
        LEAF_LIST,
        LIST,
        LIST_ELEMENT
    }

    /** shared instance of the null data node */
    public final static DataNode NULL = new NullDataNode();

    /** Special value to indicate a deleted node when diffing data nodes */
    public final static DataNode DELETED = new NullDataNode();

    /** Default values returned when a value is not set in the DB */
    /** default boolean value: false */
    final static boolean    DEFAULT_BOOLEAN = false;
    /** default long value: 0*/
    final static long       DEFAULT_LONG = 0;
    /** default double value: 0.0*/
    final static double     DEFAULT_DOUBLE = 0;
    /** default String value: "" (empty string)*/
    final static String     DEFAULT_STRING = "";
    /** default Object value: null*/
    final static Object     DEFAULT_OBJECT = null;
    /** default BigDecimalValue : BigDecimal.ZERO*/
    final static BigDecimal DEFAULT_BIG_DECIMAL = BigDecimal.ZERO;
    /** default BigIntegerValue : BigInteger.ZERO*/
    final static BigInteger DEFAULT_BIG_INTEGER = BigInteger.ZERO;
    /** default bytes[] value: empty byte array */
    final static byte[]     DEFAULT_BYTES = new byte[0];

    /** return the node type */
    public NodeType getNodeType();
    public boolean isMutable();

    /**
     * Get the digest value for the contents of the data node.
     * Data nodes that are of the same NodeType and have the same contents have
     * the same digest value, as defined by equals() between the two DigestValue
     * instances returned by getDigestValue(). Data nodes that are of different
     * types or that have different contents have different digest values with
     * a very high probability, i.e. the collision probability of a SHA-1 hash.
     * The digest value is only available for immutable nodes.
     * A BigDBException is thrown if this is called for a mutable node.
     *
     * @return digest value of the data node
     */
    public DigestValue getDigestValue() throws BigDBException;

    // Visiting data node tree
    public DataNodeVisitor.Result accept(String name, DataNodeVisitor visitor)
            throws BigDBException;
    public DataNodeVisitor.Result accept(IndexValue keyValue, DataNodeVisitor visitor)
            throws BigDBException;

    /** is this the null node? only the NullDataNode answers true
     *  @see NullDataNode
     */
    public boolean isNull();
    /** Is this a scalar data node (leaf that has a value)? */
    public boolean isScalar();
    /** Is this node iterable? */
    public boolean isIterable();
    /** Does this node support dictionary style (String name) access to its child nodes? */
    public boolean isDictionary();
    /** Does this node support indexed access (int i) to its child nodes? */
    public boolean isArray();
    /** Does this node support keyed access (IndexValue key) to its child nodes? */
    public boolean isKeyedList();

    /** methods for composite data nodes */

    /** the number of direct descendants of this datanode. 0 for leaf nodes.
     *  Note: if checking for the presence of any children, hasChildren() is preferable to childCount() > 0
     **/
    public int childCount() throws BigDBException;
    /** @return true iff the node has a non-zero number of direct descendants. */
    public boolean hasChildren() throws BigDBException;
    /** Indexed access to the child node.
     * Only applicable for unkeyed list nodes and leaf-list nodes.
     * @return the n'th child node of the array data node
     */
    public DataNode getChild(int index);
    /** return an iterator over the children of this datanode */
    @Override
    public Iterator<DataNode> iterator();

    // dictionary type access methods

    /** Get the set of the names of all of the children.
     * @return
     * @throws BigDBException
     */
    public Set<String> getChildNames() throws BigDBException;
    /** Interface for an entry (name-to-node binding) in a dictionary data node */
    public interface DictionaryEntry {
        public String getName();
        public DataNode getDataNode();
    }
    /** Iterate over the dictionary entries */
    public Iterable<DictionaryEntry> getDictionaryEntries() throws BigDBException;
    /** @return whether or not the given Node has a child named 'name'. Non-Dictionary nodes return always false */
    public boolean hasChild(String name) throws BigDBException;
    /** @return the child node whose key is keyValue. If no such child exists, Returns the <emph>NullDataNode</emph>.
     * Non-Dictionary nodes throw UnsupportedOperationException.
     * @see NullDataNode
     * */
    public DataNode getChild(String keyValue) throws BigDBException;

    // (keyed) list type access methods

    /** For keyed lists, Get the index specifier for the primary key for a list.
     * If it's not a list then this throws an UnsupportedOperationException.
     * If it's an unkeyed list then this returns null.
     * @return
     */
    public IndexSpecifier getKeySpecifier();

    public interface KeyedListEntry {
        public IndexValue getKeyValue();
        public DataNode getDataNode();
    }
    /** Iterate over the keyed list entries */
    public Iterable<KeyedListEntry> getKeyedListEntries() throws BigDBException;

    /**
     * For keyed lists,
     * @return whether or not the given keyed list has a list element child
     *         data node whose key is keyValue.
     * Non-Keyed List nodes return always false */
    public boolean hasChild(IndexValue keyValue) throws BigDBException;
    /** For keyed Lists,
     * @return the child node named name. If no such child exists, Returns the <emph>NullDataNode</emph>.
     * Non-Keyed-List nodes throw UnsupportedOperationException.
     * @see NullDataNode
     * */
    public DataNode getChild(IndexValue name) throws BigDBException;

    /// Scalar methods. Non-scalar datanodes will throw UnsupportedOperationException
    /** Is the scalar value null */
    public boolean isValueNull();
    /** @return the value stored in the leaf as a primitive boolean. If unset (null) return DEFAULT_BOOLEAN (false) */
    public boolean getBoolean() throws BigDBException;
    /** @return the value stored in the leaf as a primitive boolean. If unset (null) return the supplied default value */
    public boolean getBoolean(boolean def) throws BigDBException;
    /** @return the value stored in the leaf as a primitive long. If unset (null) return DEFAULT_LONG (0L) */
    public long getLong() throws BigDBException;
    /** @return the value stored in the leaf as a primitive long. If unset (null) return the supplied default value */
    public long getLong(long def) throws BigDBException;
    /** @return the value stored in the leaf as a primitive double. If unset (null) return DEFAULT_DOUBLE (0.0) */
    public double getDouble() throws BigDBException;
    /** @return the value stored in the leaf as a primitive double. If unset (null) return the supplied default value */
    public double getDouble(double def) throws BigDBException;

    /** @return the value stored in the leaf as a String. If unset (null) return DEFAULT_STRING ("") */
    public String getString() throws BigDBException;
    /** @return the value stored in the leaf as a String. If unset (null) return the supplied default value */
    public String getString(String def) throws BigDBException;
    /** @return the value stored in the leaf as an Object. If unset (null) return DEFAULT_OBJECT (null).
     *  Nullcheck required!
     **/
    @CheckForNull
    public Object getObject() throws BigDBException;
    /** @return the value stored in the leaf as an Object. If unset (null) return the supplied default value */
    public Object getObject(Object def) throws BigDBException;

    /** @return the value stored in the leaf as a BigDecimal. If unset (null) return DEFAULT_BIG_DECIMAL (BigDecimal.ZERO) */
    public BigDecimal getBigDecimal() throws BigDBException;
    /** @return the value stored in the leaf as an BigDecimal. If unset (null) return the supplied default value */
    public BigDecimal getBigDecimal(BigDecimal def) throws BigDBException;
    /** @return the value stored in the leaf as a BigInteger. If unset (null) return DEFAULT_BIG_INTEGER (BigInteger.ZERO) */
    public BigInteger getBigInteger() throws BigDBException;
    /** @return the value stored in the leaf as an BigInteger. If unset (null) return the supplied default value */
    public BigInteger getBigInteger(BigInteger def) throws BigDBException;

    /** return the value stored in the leaf as a byte array. If unset (null) return DEFAULT_BYTES (the empty byte array) */
    public byte[] getBytes() throws BigDBException;
    /** @return the value stored in the leaf as a byte array. If unset (null) return the supplied default value */
    public byte[] getBytes(byte[] def) throws BigDBException;
    /** @return the child data nodes of this node matching the specified query path */

    /**
     * Query for the child nodes of the current data node that match the input
     * query path.
     *
     * @param schemaNode
     *            the schema node corresponding to the data node being queried.
     * @param queryPath
     *            the path specifying the nodes to be queried. The first step in
     *            the path should correspond to the data node being queried.
     *            This allows you to query for a subset of list element nodes of
     *            a list node by specifying a predicate in the first step.
     * @return the descendant data nodes matching the specified query path.
     * @throws BigDBException
     */
    public Iterable<DataNode> query(SchemaNode schemaNode,
            LocationPathExpression queryPath) throws BigDBException;

    /** @return the child data node specified in the step */
    public DataNode getChild(Step step) throws BigDBException ;

    /**
     * Associates a data node with its path relative to another data node or
     * the root
     */
    public interface DataNodeWithPath {
        /**
         * @return the fully qualified path of the data node. The steps for all
         *         list elements have exact match predicates for all of the keys
         *         (keyed lists) or an index/integer predicate (unkeyed lists)
         */
        public LocationPathExpression getPath();
        /** @return the data node */
        public DataNode getDataNode();
    }

    /**
     * Query for the child nodes of the current data node that match the input
     * query path. If the last step in the query path maps to a list node, then
     * the query returns all of the list elements in that list that match the
     * predicate. If there is not predicate then all list elements are returned.
     *
     * @param schemaNode
     *            the schema node corresponding to the data node being queried.
     * @param queryPath
     *            the path specifying the nodes to be queried. The first step in
     *            the path should correspond to the data node being queried.
     *            This allows you to query for a subset of list element nodes of
     *            a list node by specifying a predicate in the first step.
     * @return the descendant data nodes matching the specified query path.
     *         Include the path of each matching nodes in the result.
     * @throws BigDBException
     */
    public Iterable<DataNodeWithPath> queryWithPath(SchemaNode schemaNode,
            LocationPathExpression queryPath) throws BigDBException;

    /**
     * Query for the child nodes of the current data node that match the input
     * query path.
     *
     * @param schemaNode
     *            the schema node corresponding to the data node being queried.
     * @param queryPath
     *            the path specifying the nodes to be queried. The first step in
     *            the path should correspond to the data node being queried.
     *            This allows you to query for a subset of list element nodes of
     *            a list node by specifying a predicate in the first step.
     * @param expandTrailingList
     *            if this is true and the last step in the query path
     *            corresponds to a list data node, then the query results are
     *            the list elements of that list node that match the predicate
     *            for the final step. If there is no predicate then all of the
     *            list elements are returned. If expandTrailingList is
     *            false, then the list element itself is returned rather than
     *            expanding out to the matching list elements. In that case any
     *            predicate in the query path is ignored.
     * @return the descendant data nodes matching the specified query path.
     *         Include the path of each matching nodes in the result.
     * @throws BigDBException
     */
    public Iterable<DataNodeWithPath> queryWithPath(SchemaNode schemaNode,
            LocationPathExpression queryPath, boolean expandTrailingList)
                    throws BigDBException;
}
