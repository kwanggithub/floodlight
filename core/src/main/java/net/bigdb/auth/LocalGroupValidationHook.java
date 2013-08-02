package net.bigdb.auth;

import java.util.HashSet;
import java.util.Set;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;
import net.bigdb.data.IndexValue;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.hook.ValidationHook;
import net.bigdb.schema.SchemaNode;
import net.bigdb.service.Treespace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validate cross reference when deleting a group.
 * If a group has users or permissions assigned to it, deletion will be rejected.
 *
 */
public class LocalGroupValidationHook implements ValidationHook {
    private final static Logger logger =
        LoggerFactory.getLogger(LocalGroupValidationHook.class);

    private final Treespace treespace;

    public LocalGroupValidationHook(Treespace treespace) {
        this.treespace = treespace;
    }
    private Result validatePermission(Context context) throws BigDBException {
        DataNode rootNode = context.getRootDataNode();
        // check permission assignment
        LocationPathExpression groupPath =
                LocationPathExpression.parse("/");
        SchemaNode schemaNode = treespace.getSchema().getSchemaNode(groupPath);
        LocationPathExpression queryPath =
                LocationPathExpression.parse("/core/aaa/group/rbac-permission");
        Iterable<DataNode> permissionDataNodes = rootNode.query(schemaNode, queryPath);
        Set<String> permissions = new HashSet<String>();
        for (DataNode permission : permissionDataNodes) {
            String name = permission.getString();
            if (permissions.contains(name)) {
                String message = "permission with name \"" + name +
                        "\" is already assigned to another group.";
                logger.debug("LocalGroupValidationHook: {}", message);
                return new Result(Decision.INVALID, message);
            }
            permissions.add(name);
        }
        return Result.VALID;
    }

    private Result validateAdminGroup(Context context) throws BigDBException {
        DataNode groupListDataNode = context.getHookDataNode();
        IndexValue adminGroupKey = IndexValue.fromStringKey("name",
                BigDBGroup.ADMIN.getName());
        DataNode adminGroupDataNode = groupListDataNode.getChild(adminGroupKey);

        // Make sure that the admin group has not been deleted
        if (adminGroupDataNode.isNull()) {
            String message = "deletion of predefined " +
                    BigDBGroup.ADMIN.getName() + " group is not allowed.";
            logger.debug("LocalGroupValidationHook: {}", message);
            return new Result(Decision.INVALID, message);
        }

        // Make sure that the admin user has not been deleted from admin group
        DataNode userListDataNode = adminGroupDataNode.getChild("user");
        boolean hasAdmin = false;
        for (DataNode userDataNode : userListDataNode) {
            String userName = userDataNode.getString();
            if (userName.equals(BigDBUser.PREDEFINED_ADMIN_NAME)) {
                hasAdmin = true;
                break;
            }
        }
        if (!hasAdmin) {
            String message = BigDBUser.PREDEFINED_ADMIN_NAME +
                    " user cannot be removed from the " +
                    BigDBGroup.ADMIN.getName() + " group.";
            logger.debug("LocalGroupValidationHook: {}", message);
            return new Result(Decision.INVALID, message);
        }

        return Result.VALID;
    }

    @Override
    public Result validate(Context context) throws BigDBException {
        Result result = this.validatePermission(context);
        if (result.getDecision() == Decision.INVALID) {
            return result;
        }
        return this.validateAdminGroup(context);
    }
}
