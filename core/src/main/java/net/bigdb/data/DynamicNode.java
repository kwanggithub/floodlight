package net.bigdb.data;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.bigdb.BigDBException;
import net.bigdb.schema.SchemaNode;
import net.bigdb.util.Path;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * The DynamicNode class represents a node in a tree where a dynamic data hook
 * has been registered (or one that has a descendant node where hooks have been
 * registered. The layout of the dynamic node tree mirrors the schema tree
 * except that it only contains nodes corresponding to operational state in the
 * schema, not pure config nodes. It maintains a list of the hooks that have
 * been registered with that node as well as a map of the child dynamic nodes.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
class DynamicNode {

    private final SchemaNode schemaNode;

    /** The information corresponding to a single registered hook entry */
    private static final class HookEntry {

        /** The application-defined hook to invoke */
        final DynamicDataHook hook;

        /** The operations for which the hook should be invoked */
        final EnumSet<DynamicDataHook.Operation> operations;

        HookEntry(DynamicDataHook hook, Set<DynamicDataHook.Operation> operations) {
            this.hook = hook;
            this.operations = EnumSet.copyOf(operations);
        }
    }

    public DynamicNode(SchemaNode schemaNode) {
        this.schemaNode = schemaNode;
    }

    /** The hooks that are registered for this dynamic node */
    private final List<DynamicNode.HookEntry> hookEntries =
            new CopyOnWriteArrayList<DynamicNode.HookEntry>();
    /**
     * The child dynamic nodes. The key is the name of the dynamic node, which
     * corresponds to the name of the node in the schema tree
     */
    private final ConcurrentHashMap<String, DynamicNode> childDynamicNodes =
            new ConcurrentHashMap<String, DynamicNode>();

    Iterable<DynamicDataHook> getDynamicDataHooks(
            DynamicDataHook.Operation operation) {
        List<DynamicDataHook> result = new ArrayList<DynamicDataHook>();
        for (HookEntry hookEntry: hookEntries) {
            if (hookEntry.operations.contains(operation)) {
                result.add(hookEntry.hook);
            }
        }
        return result;
    }

    Map<String, DynamicNode> getChildDynamicNodes() {
        return childDynamicNodes;
    }

    DynamicNode getDescendentDynamicNode(Path path) {
        DynamicNode dynamicNode = this;
        for (String component: path) {
            dynamicNode = dynamicNode.getChildDynamicNodes().get(component);
            if (dynamicNode == null)
                break;
        }
        return dynamicNode;
    }

    /**
     * Add a hook that's enabled for the specified operations
     *
     * @param hook the application-defined hook to invoke
     * @param operations the operations for which the hook is invoked
     */
    void addHook(DynamicDataHook hook, Set<DynamicDataHook.Operation> operations) {
        hookEntries.add(new HookEntry(hook, operations));
    }

    /**
     * Register the specified hook with the descendant node specified in the
     * path parameter. Create intermediate dynamic nodes as necessary to
     * establish the path to the specified path.
     *
     * @param path the path specifying a descendant node
     * @param hook the hook being registered
     * @param operations the operations for which the hook should be invoked
     */
    @SuppressFBWarnings(value = "AT_OPERATION_SEQUENCE_ON_CONCURRENT_ABSTRACTION")
    synchronized void addDescendentHook(Path path, DynamicDataHook hook,
            Set<DynamicDataHook.Operation> operations) {
        if (path.size() == 0) {
            addHook(hook, operations);
        } else {
            String childName = path.get(0);
            SchemaNode childSchemaNode;
            try {
                childSchemaNode = schemaNode.getChildSchemaNode(childName);
            } catch (BigDBException e) {
                throw new IllegalArgumentException("Error adding descendant hook " + hook + " to path " + path + ": Unknown child schema node: "+childName);
            }
            Path remainingPath = path.getSubPath(1);
            DynamicNode childDynamicNode = childDynamicNodes.get(childName);
            if (childDynamicNode == null) {
                childDynamicNode = new DynamicNode(childSchemaNode);
                childDynamicNodes.put(childName, childDynamicNode);
            }
            childDynamicNode.addDescendentHook(remainingPath, hook, operations);
        }
    }
}