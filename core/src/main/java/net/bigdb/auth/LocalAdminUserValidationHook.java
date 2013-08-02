package net.bigdb.auth;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;
import net.bigdb.data.IndexValue;
import net.bigdb.hook.ValidationHook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This validation hook serves as a partial protection against admin stupidity.
 *  It protects the predefined local 'admin' user account from deletion.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 *
 */
public class LocalAdminUserValidationHook implements ValidationHook {
    private final static Logger logger =
        LoggerFactory.getLogger(LocalAdminUserValidationHook.class);

    @Override
    public Result validate(Context context) throws BigDBException {
        DataNode userListDataNode = context.getHookDataNode();
        IndexValue adminKey = IndexValue.fromStringKey("user-name",
                BigDBUser.PREDEFINED_ADMIN_NAME);
        DataNode adminUserDataNode = userListDataNode.getChild(adminKey);
        if (adminUserDataNode.isNull()) {
            String msg = "deletion of predefined admin account not allowed.";
            logger.debug("LocalAdminUserValidationHook: {}", msg);
            return new Result(Decision.INVALID, msg);
        }
        String msg ="admin user ok";
        if(logger.isDebugEnabled()) {
            logger.debug("LocalAdminUserValidationHook: {}", msg);
        }
        return new Result(Decision.VALID, msg);
    }
}
