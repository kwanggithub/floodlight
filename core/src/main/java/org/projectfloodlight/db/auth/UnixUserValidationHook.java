package org.projectfloodlight.db.auth;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.hook.ValidationHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

/**
 * This validation hook tries to prevent the creation of users in the local
 * authenticator that are also in the /etc/passwd and /etc/shadow files. The
 * 'admin' user is excepted, as is any user that has a suitably high UID.
 * Shamelessly copied from LocalAdminUserValidationHook.
 *
 * @author Carl Roth <carl.roth@bigswitch.com>
 */

public class UnixUserValidationHook implements ValidationHook {

    private final static Logger logger = LoggerFactory.getLogger(UnixUserValidationHook.class);

    static private final int REMOTEUSER_UID = 10000;
    // 'remoteuser', UID unfortunately embedded in libpam-rest

    private long passwdMtime = -1;
    private volatile Map<String, Integer> usersMap = ImmutableMap.of();

    private final File passwdFile;
    static File PASSWD = new File("/etc/passwd");

    public UnixUserValidationHook(File passwdFile) {
        this.passwdFile = passwdFile;
    }

    /** Conditionally parse the passwd file and update the usersMap
     * and passwdMtime markers.  Both items are updated atomically.
     *
     * On future invocations, if the passwd file is updated (mtime advances)
     * then the usersMap is regenerated.
     *
     */
    private synchronized long maybeParsePasswd() {

        // else, make sure the user being updated is not a reserved user
        logger.debug("opening {} to get reserved users", passwdFile.getPath());

        long passwdMtime = passwdFile.lastModified();
        if (passwdMtime == 0) {
            logger.warn("cannot read {}", passwdFile.getPath());
            return this.passwdMtime;
        }

        // already parsed once
        if (this.passwdMtime >= passwdMtime)
            return this.passwdMtime;
        this.passwdMtime = passwdMtime;

        int fl = (int) passwdFile.length();
        if (fl == 0) {
            logger.error("empty or missing {}", passwdFile.getPath());
            return this.passwdMtime;
        }

        ImmutableMap.Builder<String, Integer> b = ImmutableMap.<String, Integer>builder();
        try {
            for (String line : Files.readLines(passwdFile, Charsets.US_ASCII)) {
                if ((line.length() > 0)
                        && (line.charAt(0) == '#'))
                    continue;
                String[] words = line.split(":");
                if (words.length < 7) {
                    logger.warn("invalid password entry in {}: {}", passwdFile.getPath(), line);
                    continue;
                }
                try {
                    b.put(words[0], Integer.parseInt(words[2]));
                } catch (NumberFormatException e) {
                    logger.warn("invalid password entry in {}: {}", passwdFile.getPath(), line);
                }
            }
        } catch (IOException e) {
            logger.error("cannot read {}", passwdFile.getPath(), e);
            return this.passwdMtime;
        }

        usersMap = b.build();
        return this.passwdMtime;
    }

    @Override
    public Result validate(Context context) throws BigDBException {

        // DataNode oldDataNode = context.getOldHookDataNode();
        DataNode newDataNode = context.getHookDataNode();

        if (newDataNode.isNull()) {
            String msg = "deleting user OK";
            return new Result(Decision.VALID, msg);
        }

        String userName = newDataNode.getChild("user-name").getString();

        // manipulating 'admin' in the local user database is OK
        if (userName.equals(BigDBUser.PREDEFINED_ADMIN_NAME)) {
            String msg = "manipulating admin-ish user ok";
            return new Result(Decision.VALID, msg);
        }

        long pmt = maybeParsePasswd();
        if (pmt <= 0) {
            String msg = "unable to verify user locally";
            return new Result(Decision.VALID, msg);
        }

        Integer uid = usersMap.get(userName);
        if (uid == null) {
            String msg = "remote/foreign user name ok";
            return new Result(Decision.VALID, msg);
        }

        if (uid.intValue() >= REMOTEUSER_UID) {
            String msg = "non-reserved user manipulation ok";
            return new Result(Decision.VALID, msg);
        }

        String msg = String.format("cannot manipulate reserved user name %s (uid %d)",
                                   userName, uid);
        logger.error(msg);
        return new Result(Decision.INVALID, msg);

        // else, this user is not in /etc/passwd
    }
}
