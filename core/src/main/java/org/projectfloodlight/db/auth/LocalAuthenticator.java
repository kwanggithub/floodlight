package org.projectfloodlight.db.auth;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.BigDBException.Type;
import org.projectfloodlight.db.auth.password.HashedPassword;
import org.projectfloodlight.db.auth.password.PasswordHasher;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeSet;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.rest.auth.LoginRequest;
import org.projectfloodlight.db.service.Service;
import org.projectfloodlight.db.service.Treespace;
import org.restlet.data.ClientInfo;
import org.restlet.data.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Local implementation of the Authenticator class, based on bigdb
 * entries.
 *
 * @author    Shudong Zhou
 */
public class LocalAuthenticator implements AuthenticatorMethod {

    private final static Logger log = LoggerFactory.getLogger(LocalAuthenticator.class);

    public static final String NAME = "local";
    private final Service bigDB;

    private final PasswordHasher passwordHasher;

    public LocalAuthenticator(Service svc, PasswordHasher passwordHasher) {
        this.bigDB = svc;
        this.passwordHasher = passwordHasher;
    }

    private AuthnResult authn(String login, String password)
            throws AuthenticationException {
        if(log.isDebugEnabled())
            log.debug("authenticating as '{}' (password hidden)", login);

        String stored_passwd;
        String fullname;
        try {
            DataNode safeUserNode = getUser(login);
            if(safeUserNode.isNull() || Strings.isNullOrEmpty(safeUserNode.getChild("user-name").getString())) {
                return AuthnResult.notAuthenticated();
            }

            stored_passwd = safeUserNode.getChild("password").getString();
            fullname = safeUserNode.getChild("full-name").getString();
        } catch (BigDBException e) {
            throw new AuthenticationException(Status.CONNECTOR_ERROR_INTERNAL,
                                              "Failed to query bigDB", e.getMessage());
        }

        HashedPassword passwordInfo;
        try {
            passwordInfo = HashedPassword.parse(stored_passwd);
        } catch (IllegalArgumentException e) {
            log.warn("Error parsing stored password info '"+stored_passwd+"': "+e.getMessage(), e);
            return AuthnResult.notAuthenticated();
        }

        if(!passwordHasher.checkPassword(password, passwordInfo)) {
            return AuthnResult.notAuthenticated();
        }

        return AuthnResult.success(login, fullname);
    }

    private AuthzResult authz(AuthnResult context)
            throws AuthenticationException {
        if(log.isDebugEnabled())
            log.debug("authorizing as '{}'", context.getPrincipal());
        Set<String> groups = new HashSet<String>();
        try {
            // Need to read group list from group node
            Set<String> localGroups = this.getLocalGroups(context.getPrincipal());
            groups.addAll(localGroups);
        } catch (BigDBException e) {
            throw new AuthenticationException(Status.CONNECTOR_ERROR_INTERNAL,
                                              "Failed to query bigDB", e.getMessage());
        }

        return AuthzResult.success(groups);
    }

    @Override
    public AuthnResult getPrincipal(LoginRequest request,
                                    ClientInfo clientInfo) throws AuthenticationException {
        return authn(request.getUser(), request.getPassword());
    }

    @Override
    public AuthzResult getGroups(AuthnResult context, LoginRequest request,
                                 ClientInfo clientInfo) throws AuthenticationException {
        return authz(context);
    }

    private Set<String> getLocalGroups(String userName)
            throws BigDBException {
        Query query = Query.parse("/core/aaa/group");
        DataNodeSet dataNodeSet = bigDB.getTreespace("controller")
                .queryData(query, AuthContext.SYSTEM);
        Set<String> groups = new TreeSet<String>();
        for (DataNode n : dataNodeSet) {
            String groupName = n.getChild("name").getString();
            DataNode users = n.getChild("user");
            for (DataNode user : users) {
                String un = user.getString();
                if (un.equals(userName)) {
                    groups.add(groupName);
                }
            }
        }
        return groups;
    }
    private DataNode getUser(String login) throws BigDBException {
        Query query = Query.parse("/core/aaa/local-user[user-name=$login]", "login", login);
        DataNodeSet dataNodeSet = bigDB.getTreespace("controller")
                .queryData(query, AuthContext.SYSTEM);
        DataNode user = dataNodeSet.getSingleDataNode();
        
        AuthConfig config = bigDB.getAuthService().getConfig();
        if (user.isNull() && 
            BigDBUser.PREDEFINED_ADMIN_NAME.equals(login) &&
            config.getParam(AuthConfig.ALLOW_EMPTY_ADMIN)) {
            resetAdminPassword("");
            return getUser(login);
        }
        
        return user;
    }

    public void resetAdminPassword(String password) {
        log.info("Resetting admin password");
        try {
            Query query = Query.parse("/core/aaa/local-user[user-name=$name]",
                    "name", BigDBUser.PREDEFINED_ADMIN_NAME);

            List<?> replaceData = ImmutableList.of(
                    ImmutableMap.of(
                            "user-name", BigDBUser.PREDEFINED_ADMIN_NAME,
                            "password", passwordHasher.hashPassword(password).toString(),
                            "full-name", "Default admin"
                            )
            );


            ByteArrayInputStream input;
            try {
                input = new ByteArrayInputStream(new ObjectMapper().writeValueAsBytes(replaceData));
            } catch (Exception e) {
                throw new RuntimeException("Error in generating JSON", e);
            }

            Treespace ts = bigDB.getTreespace("controller");
            ts.replaceData(query, Treespace.DataFormat.JSON, input, AuthContext.SYSTEM);
            // insert admin group
            // if the group already exists, we do not insert.
            query = Query.parse("/core/aaa/group[name=$group-name]", "group-name",
                                BigDBGroup.ADMIN.getName());

            DataNode adminGroup = ts.queryData(query, AuthContext.SYSTEM).getSingleDataNode();

            if (adminGroup.isNull()) {
                log.info("Add admin group");
                // admin group does not exist, insert it.
                query = Query.parse("/core/aaa/group");
                String group = "{\"name\":\"" + BigDBGroup.ADMIN.getName() + 
                            "\", \"user\": [\"" + 
                            BigDBUser.PREDEFINED_ADMIN_NAME + "\"]}";
                try {
                    input = new ByteArrayInputStream(group.getBytes("UTF-8"));
                    ts.insertData(query, Treespace.DataFormat.JSON, input,
                                  AuthContext.SYSTEM);
                } catch (Exception e) {
                    throw new RuntimeException("Error in adding group ", e);
                }
            }
        } catch (BigDBException e) {
            throw new RuntimeException("Error resetting admin user password: "+e.getMessage(),e);
        }
    }

    public void changeUserPassword(String user, String oldPassword, String newPassword)
            throws BigDBException, AuthenticationException {
        AuthnResult result = authn(user, oldPassword);
        if (!result.isSuccess()) {
            throw new BigDBException("change password: invalid user/password combination", Type.FORBIDDEN);
        }

        log.info("Resetting password for user "+user);
        Query query =
                Query.parse("/core/aaa/local-user[user-name=$name]/password", "name", user);
        HashedPassword newHash = passwordHasher.hashPassword(newPassword);
        StringBuilder buf = new StringBuilder();
        buf.append('"').append(JsonStringEncoder.getInstance().quoteAsString(newHash.getHashedPassword())).append('"');
        ByteArrayInputStream input = new ByteArrayInputStream(buf.toString().getBytes(Charsets.UTF_8));
        bigDB.getTreespace("controller").replaceData(query, Treespace.DataFormat.JSON,
                input, AuthContext.SYSTEM);
    }

}
