package net.bigdb.auth;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;

import net.bigdb.BigDBException;
import net.bigdb.BigDBInternalError;
import net.bigdb.data.DataNode;
import net.bigdb.data.DataNodeSet;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.hook.WatchHook;
import net.bigdb.query.Query;
import net.bigdb.query.Step;
import net.bigdb.service.Treespace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Watch hook that watches for deletion of local users and removes those users
 * from any groups that contain that user.
 *
 * FIXME: This really shouldn't be a watch hook, since watch hooks should really
 * just react to changes to the tree, not initiate further modifications to the
 * tree. And the behavior of this in an active/standby configuration (much less
 * active/active) is problematic. Currently this happens to work OK (the watch
 * hook is called on the slave but the groups will have already been cleaned up
 * so there won't be anything to do), but at some point we'll need to fix this.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class LocalUserWatchHook implements WatchHook {

    protected static final Logger logger =
            LoggerFactory.getLogger(LocalUserWatchHook.class);

    private final Treespace treespace;

    public LocalUserWatchHook(Treespace treespace) {
        this.treespace = treespace;
    }

    @Override
    public void watch(Context context) throws BigDBException {

        DataNode userDataNode = context.getNewHookDataNode();

        // Check if it's a deletion. If not, we're done.
        if (!userDataNode.isNull())
            return;

        // Determine the user-name of the deleted user
        LocationPathExpression userPath = context.getHookPath();
        Step userStep = userPath.getStep(userPath.size() - 1);
        String deletedUserName = userStep.getExactMatchPredicateString("user-name");

        // Query for all of the groups
        Query allGroupsQuery = Query.builder()
                .setBasePath("/core/aaa/group")
                .setIncludedStateType(Query.StateType.CONFIG)
                .getQuery();
        DataNodeSet allGroups = treespace.queryData(allGroupsQuery, AuthContext.SYSTEM);

        ObjectMapper mapper = new ObjectMapper();

        // Iterate over the groups to see which ones contain the deleted user
        for (DataNode group: allGroups) {
            DataNode users = group.getChild("user");
            Set<String> updatedUsers = new TreeSet<String>();
            boolean changed = false;
            for (DataNode user: users) {
                String userName = user.getString();
                if (userName.equals(deletedUserName)) {
                    changed = true;
                } else {
                    updatedUsers.add(userName);
                }
            }
            if (changed) {
                String groupName = group.getChild("name").getString();
                Query userQuery = Query.parse("/core/aaa/group[name=$group]/user",
                        "group", groupName);
                byte[] updatedUserBytes;
                try {
                    updatedUserBytes = mapper.writeValueAsBytes(updatedUsers);
                }
                catch (Exception e) {
                    throw new BigDBInternalError("Error serializing user data", e);
                }
                InputStream data = new ByteArrayInputStream(updatedUserBytes);
                try {
                    treespace.replaceData(userQuery, Treespace.DataFormat.JSON,
                            data, AuthContext.SYSTEM);
                    logger.debug("Deleted user {} from group {}", deletedUserName, groupName);
                }
                catch (BigDBException e) {
                    logger.debug("Error deleting user {} from group {}",
                            new Object[] {deletedUserName, groupName}, e);
                }
            }
        }
    }

}
