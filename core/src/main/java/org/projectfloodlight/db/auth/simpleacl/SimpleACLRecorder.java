package org.projectfloodlight.db.auth.simpleacl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.hook.AuthorizationHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;

/**
 * a special authorizer that will <b>*record*</b> (not replay!) an ACL file. Can
 * be used to easily create a white list configuration for a set of CLI
 * commands. Example invocation:
 *
 * <code>
 *  java -Dorg.projectfloodlight.db.useAuth=true \
 *    -Dorg.projectfloodlight.db.auth.defaultAuthorizer=org.projectfloodlight.db.auth.RestrictedAuthorizer
 *    -Dorg.projectfloodlight.db.auth.restrictedPolicyRecord=true
 *    -Dorg.projectfloodlight.db.auth.restrictedPolicyFile=restricted.acl
 *    -Dorg.projectfloodlight.db.auth.restrictedPolicyDefaultResult=STRONG_ACCEPT
 *    -jar target/floodlight.jar
 *  </code>
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class SimpleACLRecorder implements AuthorizationHook {
    protected static Logger logger = LoggerFactory
            .getLogger(SimpleACLRecorder.class);

    private final Result defaultResult;
    private final SimpleACL acl;
    private final PrintWriter appender;

    public SimpleACLRecorder(Result defaultResult, String file)
            throws IOException {
        this(defaultResult, new File(file));
    }

    public SimpleACLRecorder(Result defaultResult, File file)
            throws IOException {
        this.defaultResult = defaultResult;

        if (file.exists())
            this.acl = loadACL(file);
        else
            this.acl = new SimpleACL();

        this.appender =
                new PrintWriter(new OutputStreamWriter(new FileOutputStream(
                        file, true), Charsets.UTF_8));
    }

    private static SimpleACL loadACL(final File file) throws IOException {
        FileInputStream is = new FileInputStream(file);
        SimpleACL acl = SimpleACL.fromStream(is);
        is.close();
        return acl;
    }

    private void appendEntry(SimpleACLEntry newEntry) {
        appender.println(newEntry);
        appender.flush();
    }

    public void close() {
        appender.close();
    }

    @Override
    public Result authorize(Context context) throws BigDBException {

        LocationPathExpression hookPath = context.getHookPath();
        //recordMutatedPaths(hookPath, hookDataNodeDiffs);
        AuthorizationHook.Operation operation = context.getOperation();
        SimpleACLEntry entry =
                new SimpleACLEntry(new SimpleACLMatch(
                        operation, hookPath), defaultResult);
        if (!acl.getEntries().contains(entry)) {
            synchronized (this) {
                // while acl is threadsafe, no guarantee here that some other
                // thread my not come first
                // so synchronize here
                if (!acl.getEntries().contains(entry)) {
                    acl.getEntries().add(entry);
                    appendEntry(entry);
                }
            }
        }
        return defaultResult;
    }

}
