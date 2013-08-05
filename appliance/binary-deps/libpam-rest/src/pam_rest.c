/**********************************************************************
 *
 * pam_rest.c
 *
 **********************************************************************/

#include <security/pam_misc.h>
#include "pam_rest.h"

int
pam_sm_authenticate(pam_handle_t *pamh,	 
                    int flags,
                    int argc,
                    const char **argv)
{
  int code;

  pam_rest_t *data = getModuleData(pamh);
  if (data == NULL)
    return PAM_AUTHINFO_UNAVAIL;

  if (flags & PAM_SILENT)
    data->silent = 1;
  if (flags & PAM_DISALLOW_NULL_AUTHTOK)
    data->allowNullAuthtok = 0;

  PAM_LOG_DEBUG(pamh, "hello");

  /*
   * 0. get/initialize the pam data with pam_get_data/pam_set_data
   * 1. parse options
   */

  if (parseOpts(pamh, argc, argv) < 0)
    return PAM_AUTHINFO_UNAVAIL;

  /*
   * 2. get user via pam_get_user(..., ..., "Username: ")
   */

  char *user;
  code = pam_get_user(pamh, (void *) &user, "Username: ");
  if (code != PAM_SUCCESS)
    return PAM_AUTHINFO_UNAVAIL;
  PAM_LOG_DEBUG(pamh, "found user %s", user);

  /* FIXME: keep track of the password,
   * it needs to be freed
   */

  /*
   * 3. get password with pam_get_item(..., PAM_AUTHTOK, ...)
   * 4. otherwise, get password with converse()
   */

  char *pass, *heapPass;
  code = getPassword(pamh, &heapPass);
  if (code != PAM_SUCCESS)
    {
      PAM_LOG_ERROR(pamh, "getPassword failed");
      return PAM_AUTHINFO_UNAVAIL;
    }
  PAM_LOG_DEBUG(pamh, "found pass %s", heapPass);
  
  /* move this onto the stack */
  pass = alloca(strlen(heapPass)+1);
  strcpy(pass, heapPass);
  free(heapPass);

  /*
   * 5. save password with pam_set_item(...)
   *    (so that the token can be re-used for /etc/passwd login)
   */

  code = pam_set_item(pamh, PAM_AUTHTOK, (const void *) pass);
  if (code != PAM_SUCCESS)
    {
      PAM_LOG_ERROR(pamh, "failed to set authtok");
      return PAM_AUTHINFO_UNAVAIL;
    }

  /*
   * 6. get remote host and tty (port)
   */

  char *port;
  code = getPort(pamh, &port);
  if (code != PAM_SUCCESS)
    {
      PAM_LOG_ERROR(pamh, "failed to get port");
      return PAM_AUTHINFO_UNAVAIL;
    }
  PAM_LOG_DEBUG(pamh, "found tty/port %s", port);

  char *host;
  code = getHost(pamh, &host);

  if (code != PAM_SUCCESS)
    {
      PAM_LOG_ERROR(pamh, "failed to get remote host");
      return PAM_AUTHINFO_UNAVAIL;
    }

  PAM_LOG_DEBUG(pamh, "found host %s", host);
  
  /*
   * 7. construct REST request
   */

  switch (data->method)
    {
    case PAM_METHOD_GET:
      code = getSession(pamh, user, pass, host, port);
      break;
    case PAM_METHOD_POST:
      switch (data->postType)
        {
        case PAM_TYPE_FORM:
          code = postSessionForm(pamh, user, pass, host, port);
          break;
        case PAM_TYPE_JSON:
          code = postSessionJson(pamh, user, pass, host, port);
          break;
        default:
          PAM_LOG_ERROR(pamh, "invalid or undefined POST type");
          code = PAM_AUTHINFO_UNAVAIL;
        }
      break;
    default:
      PAM_LOG_ERROR(pamh, "invalid or undefined REST method");
      code = PAM_AUTHINFO_UNAVAIL;
    }

  if (code == PAM_AUTHINFO_UNAVAIL && !data->failureWarned)
    {
      data->failureWarned = 1;
      send_error(pamh, "Authentication is currently unavailable");
    }

  if (code != PAM_SUCCESS)
    return code;

  code = parseResponse(pamh);
  if (code != PAM_SUCCESS)
    return code;

  /* PAM_AUTH_ERR if no Set-Cookie field (otherwise not fatal) */
  code = parseHeaders(pamh);
  switch (code)
    {
    case PAM_SUCCESS:
    case PAM_AUTH_ERR:
      break;

    default:
      return code;
    }

  code = parseJson(pamh);
  if (code != PAM_SUCCESS)
    return code;

  /* json parsing succeeded, but there is still no session cookie */
  if (data->session == NULL)
    {
      PAM_LOG_ERROR(pamh, "unable to retrieve a session cookie");
      return PAM_AUTH_ERR;
    }

  unsetenv(ENV_NAME);
  setenv(ENV_NAME, data->session, 1);
  pam_misc_setenv(pamh, ENV_NAME, data->session, 0);

  return PAM_SUCCESS;
}

int
send_error (pam_handle_t * pamh, const char* message)
{
    const struct pam_message msg = {
        .msg_style = PAM_ERROR_MSG,
        .msg = message
    };
    const struct pam_message *msgp = &msg;
    struct pam_response *resp = NULL;
    struct pam_conv *conv;
    int retval;

    retval = pam_get_item(pamh, PAM_CONV, (void *)&conv);
    if (retval != PAM_SUCCESS) {
        return retval;
    }

    return conv->conv(1, &msgp, &resp, conv->appdata_ptr);
}

int
pam_sm_setcred (pam_handle_t * pamh, int flags,
                int argc, const char **argv)
{
  PAM_LOG_DEBUG(pamh, "hello");

  /* this is a no-op; we did the real test above */
  return PAM_SUCCESS;
}

/*
 * Is the user authorized for login?
 *
 * If we assume that the REST api token infers login,
 * then we can just declare success as long as there is *any* cookie.
 *
 * Otherwise, we need to do a supplemental REST request to get the
 * group memberships
 *
 * In any case, don't authorize a user that was not regnized during
 * authentication.
 */

int
pam_sm_acct_mgmt(pam_handle_t *pamh, int flags,
                 int argc, const char **argv)
{
  PAM_LOG_DEBUG(pamh, "hello");

  pam_rest_t *data = getModuleData(pamh);

  /*
   * if the user does not have a valid cookie from auth
   * (from this session) then the user is unknown to REST.
   * Do not allow us to export the environment variable
   * to jump-start authentication (that's session hijacking)
   */

  if (data == NULL)
    {
      PAM_LOG_ERROR(pamh, "NULL module data");
      return PAM_USER_UNKNOWN;
    }
  if (data->session == NULL)
    {
      PAM_LOG_ERROR(pamh, "NULL session");
      return PAM_USER_UNKNOWN;
    }

  /*
   * if the group memberships do not imply Cli access
   * return PAM_PERM_DENIED;
   */

  PAM_LOG_DEBUG(pamh, "account verified (session %s)", data->session);

  /* this is a no-op; we did the real test above */
  return PAM_SUCCESS;
}

/* login/logout accounting */
int
pam_sm_open_session(pam_handle_t *pamh, int flags,
                    int argc, const char **argv)
{
  PAM_LOG_DEBUG(pamh, "hello");
  
  /* REST request to log a cli opening */
  /* optionally generate local syslog call */

  return PAM_SUCCESS;
}

int
pam_sm_close_session(pam_handle_t *pamh, int flags,
                     int argc, const char **argv)
{
  PAM_LOG_DEBUG(pamh, "hello");

  /* REST request to log a cli closing */
  /* optionally generate local syslog call */

  return PAM_SUCCESS;
}

