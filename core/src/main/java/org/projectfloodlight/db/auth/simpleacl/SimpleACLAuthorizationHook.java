package org.projectfloodlight.db.auth.simpleacl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.hook.AuthorizationHook;
import org.projectfloodlight.util.FileWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

public class SimpleACLAuthorizationHook implements AuthorizationHook {

    private final static Logger logger = LoggerFactory
            .getLogger(SimpleACLAuthorizationHook.class);

    private final AuthorizationHook.Result defaultResult;
    private volatile SimpleACL acl;
    private FileWatcher watcher;

    public SimpleACLAuthorizationHook(AuthorizationHook.Result defaultResult) {
        this.defaultResult = defaultResult;
        this.acl = new SimpleACL();
        this.watcher = null;
    }

    public void load(String fileOrResourceName) throws IOException {
        File file = new File(fileOrResourceName);
        if (file.exists()) {
            this.loadAndWatch(file);
        } else {
            URL url = getClass().getResource(fileOrResourceName);
            if (url != null)
                this.load(url);
            else 
                logger.debug("Neither file nor resource " + 
                             fileOrResourceName + " found");
        }
    }

    public void loadAndWatch(final File file) throws IOException {
        final SimpleACLAuthorizationHook this_ = this;
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }

        watcher =
                FileWatcher.Builder.create().mustExist(true).loadOnStart(true)
                        .watchInterval(2000).safetyPeriod(1000).onChanged(
                                new FileWatcher.Loader<SimpleACL>() {
                                    @Override
                                    public SimpleACL load(InputStream in)
                                            throws IOException {
                                        return SimpleACL.fromStream(in);
                                    }
                                }).onSuccessfullyReloaded(
                                new FileWatcher.Acceptor<SimpleACL>() {
                                    @Override
                                    public void accept(SimpleACL obj) {
                                        this_.acl = obj;
                                        logger.info("Successfully reloaded ACL from file " +
                                                file);
                                    }
                                }).watchFile(file);
    }

    public void load(URL aclUrl) throws IOException {
        if (Objects.equal("file", aclUrl.getProtocol())) {
            // try to load file urls as files, so we can watch for changes
            try {
                loadAndWatch(new File(aclUrl.toURI()));
                return;
            } catch (URISyntaxException e) {
                // well it was worth a try
                logger.info("Could not not load file url as file - trying the old fashioned way");
            }
        }
        acl = SimpleACL.fromStream(aclUrl.openStream());
        logger.info("Successfully reloaded ACL from URL " + aclUrl);
    }

    public void load(InputStream in) throws IOException {
        acl = SimpleACL.fromStream(in);
        logger.info("Successfully reloaded ACL from Input Stream " + in);
    }

    public SimpleACL getAcl() {
        return acl;
    }

    public void setAcl(SimpleACL acl) {
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
        this.acl = acl;
    }

    @Override
    public Result authorize(Context context) throws BigDBException {
        AuthorizationHook.Operation operation = context.getOperation();
        LocationPathExpression hookPath = context.getHookPath();
        DataNode hookDataNode = context.getWrittenHookDataNode();
        AuthContext authContext = context.getAuthContext();
        for (SimpleACLEntry e : acl.getEntries()) {
            if (e.getMatch().matches(operation, hookPath, hookDataNode,
                    authContext)) {
                return e.getResult();
            }
        }
        return defaultResult;
    }
}
