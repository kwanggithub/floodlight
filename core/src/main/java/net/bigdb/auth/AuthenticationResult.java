package net.bigdb.auth;


import org.restlet.data.Status;

/**
 * Immutable bean that conveys the success or failure of an authentication
 * attempt. Use this for any regular authentication result.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class AuthenticationResult {
    private static final String invalid_user_password = "invalid user/password combination";

    /** whether the authentication was a success */
    private final boolean success;

    /** contains information on the user iff success == true */
    private final BigDBUser user;

    /**
     * HTTP status code for this result. Typically, 200 for success, 40x for
     * failures
     */
    private final Status status;

    /**
     * 'internal' reason for the failure. Not sent to client. E.g.,
     * "user not in database"
     */
    private final String internalReason;

    /**
     * external reason for the failure. Should not convey confident data.
     * "invalid user/password combination"
     */
    private final String externalReason;

    /** ugly constructor. use the factories */
    private AuthenticationResult(boolean success, BigDBUser user, Status status,
            String internalReason, String externalReason) {
        this.success = success;
        this.user = user;
        this.status = status;
        this.internalReason = internalReason;
        this.externalReason = externalReason;
    }

    /** return an authentication result for a successful authentication */
    public final static AuthenticationResult success(BigDBUser user) {
        return new AuthenticationResult(true, user, Status.SUCCESS_OK, null, null);
    }

    /** return an authentication result for a failed authentication */
    public final static AuthenticationResult failure(Status status, String internalReason,
            String externalReason) {
        return new AuthenticationResult(false, null, status, internalReason, externalReason);
    }

    public boolean isSuccess() {
        return success;
    }

    public BigDBUser getUser() {
        return user;
    }

    public Status getStatus() {
        return status;
    }

    public String getInternalReason() {
        return internalReason;
    }

    public String getExternalReason() {
        return externalReason;
    }

    public static AuthenticationResult userNotFound(String login) {
        return failure(Status.CLIENT_ERROR_UNAUTHORIZED,
                String.format("user %s: not found", login), invalid_user_password);
    }

    public static AuthenticationResult wrongPassword(String login) {
        return failure(Status.CLIENT_ERROR_UNAUTHORIZED,
                String.format("user %s: password incorrect", login), invalid_user_password);
    }

    public static AuthenticationResult networkError(String login) {
        return failure(Status.CLIENT_ERROR_UNAUTHORIZED,
                String.format("user %s: network error", login), invalid_user_password);
    }

    public static AuthenticationResult notAuthorized(String login) {
        return failure(Status.CLIENT_ERROR_UNAUTHORIZED,
                String.format("user %s: not authorized", login), invalid_user_password);
    }

}
