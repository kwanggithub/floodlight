/**********************************************************************
 *
 * pam_rest.h
 *
 **********************************************************************/

#ifndef HAVE_PAM_REST_H
#define HAVE_PAM_REST_H 1

#ifdef HAVE_CONFIG_H
#  include "config.h"
#endif

#ifdef HAVE_STDLIB_H
#  include <stdlib.h>
#endif

#ifdef HAVE_UNISTD_H
#  include <unistd.h>
#endif

#ifdef HAVE_STRING_H
#  include <string.h>
#endif

#ifdef HAVE_ALLOCA_H
#  include <alloca.h>
#endif

#include <curl/curl.h>

#define PAM_SM_AUTH
#define PAM_SM_ACCOUNT
#define PAM_SM_SESSION

#include <security/pam_modules.h>

#include "bufUtils.h"
#include "pamUtils.h"

PAM_EXTERN int
pam_sm_authenticate(pam_handle_t *pamh,	 
                    int flags,
                    int argc,
                    const char **argv);

PAM_EXTERN int
pam_sm_setcred(pam_handle_t *pamh,
               int flags,
               int argc,
               const char **argv);

PAM_EXTERN int
pam_sm_acct_mgmt(pam_handle_t * pamh, int flags,
                 int argc, const char **argv);

PAM_EXTERN int
pam_sm_open_session(pam_handle_t * pamh, int flags,
                    int argc, const char **argv);

PAM_EXTERN int
pam_sm_close_session(pam_handle_t * pamh, int flags,
                     int argc, const char **argv);

#endif /* PAM_REST_H */

