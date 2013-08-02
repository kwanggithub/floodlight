package net.bigdb.rest.auth;

import java.util.Map;

import net.bigdb.BigDBException;
import net.bigdb.auth.AuthConfig;
import net.bigdb.rest.BigDBResource;
import net.bigdb.rest.auth.LoginResource.LoginResult;

import org.restlet.resource.Get;
import org.restlet.resource.Post;

/** Restlet Resource handling the AAA login protocol. Note that his is part
 *  of the access mechanism, not BigDB as such.
 *
 *  @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class LoginAuthDisabledResource extends BigDBResource {
    @Post
    public LoginResult doPost(Map<?,?> ignored) throws BigDBException {
        return doGet();
    }

    @Get
    public LoginResult doGet() throws BigDBException {
        return LoginResult.failure("Floodlight: Authentication disabled. Run floodlight with system property "+AuthConfig.SYSPROP_USE_AUTH + " set to true to enable");
    }

}
