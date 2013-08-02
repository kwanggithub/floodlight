package net.bigdb.auth;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;
import net.bigdb.data.annotation.BigDBInsert;
import net.bigdb.data.annotation.BigDBParam;
import net.bigdb.data.annotation.BigDBReplace;
import net.floodlightcontroller.bigdb.FloodlightResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dynamic BigDB Resource allowing a non-priviledged user to change their password
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class LocalUserPasswordChangeResource extends FloodlightResource {
    private final static Logger logger =
        LoggerFactory.getLogger(LocalUserPasswordChangeResource.class);

    private final LocalAuthenticator authenticator;

    public LocalUserPasswordChangeResource(LocalAuthenticator authenticator) throws BigDBException {
        this.authenticator = authenticator;
    }

    @BigDBReplace
    @BigDBInsert
    public void changePassword(@BigDBParam("mutation-data") DataNode node) throws BigDBException, AuthenticationException {
        logger.debug("changePassword: "+node);

        String user = node.getChild("user-name").getString();
        if("".equals(user)) {
            throw new BigDBException("no user specified");
        }
        String oldPassword = node.getChild("old-password").getString();
        String newPassword = node.getChild("new-password").getString();
        authenticator.changeUserPassword(user, oldPassword, newPassword);
    }

}
