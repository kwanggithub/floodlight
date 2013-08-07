package org.projectfloodlight.db.auth;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.FloodlightResource;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.annotation.BigDBInsert;
import org.projectfloodlight.db.data.annotation.BigDBParam;
import org.projectfloodlight.db.data.annotation.BigDBReplace;
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
