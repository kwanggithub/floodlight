/**********************************************************************
 *
 * test_map_rest.c
 *
 **********************************************************************/

#include <security/pam_appl.h>

#ifdef HAVE_CONFIG_H
#  include "config.h"
#endif

#ifdef HAVE_STDLIB_H
#  include <stdlib.h>
#endif

#ifdef HAVE_STRING_H
#  include <string.h>
#endif

#ifdef HAVE_STDARG_H
#  include <stdarg.h>
#endif

#include <stdio.h>

#ifndef USER
#  define USER "testuser"
#endif

#ifndef PASSWORD
#  define PASSWORD "secret"
#endif

static inline void
__log_info__(const char *file, int line, const char *function,
             const char *prefix, const char *msg, ...)
{
  va_list ap;

  fprintf(stderr, "%s:%d:%s:%s ", file, line, function, prefix);
  
  va_start(ap, msg);
  vfprintf(stderr, msg, ap);
  va_end(ap);

  fprintf(stderr, "\n");
  
}

#define LOG_INFO(msg, ...)  __log_info__(__FILE__, __LINE__, __FUNCTION__, "", msg, ##__VA_ARGS__)
#define LOG_ERROR(msg, ...)  __log_info__(__FILE__, __LINE__, __FUNCTION__, "*** ", msg, ##__VA_ARGS__)

int
converse(int num_msg,
         const struct pam_message **msg,
         struct pam_response **respHandle,
         void *appData)
{
  int i;

  struct pam_response *resp = malloc(num_msg*sizeof(struct pam_response));

  for (i = 0; i < num_msg; ++i)
    {
      resp[i].resp = 0;
      resp[i].resp_retcode = 0;

      switch (msg[i]->msg_style)
        {
        case PAM_TEXT_INFO:
          LOG_INFO("PAM_TEXT_INFO[%d]: %s", i, msg[i]->msg);
          break;

        case PAM_ERROR_MSG:
          LOG_INFO("PAM_ERROR_MSG[%d]: %s", i, msg[i]->msg);
          break;

        case PAM_PROMPT_ECHO_OFF:
          LOG_INFO("PAM_PROMPT_ECHO_OFF[%d]: %s", i, msg[i]->msg);
          LOG_INFO("returning '%s'", PASSWORD);
          resp[i].resp = strdup(PASSWORD);
          break;

        case PAM_PROMPT_ECHO_ON:
          LOG_INFO("PAM_PROMPT_ECHO_ON[%d]: %s", i, msg[i]->msg);
          resp[i].resp = strdup("secretsecret");
          LOG_INFO("returning 'secretsecret'");
          break;

        default:
          LOG_INFO("*** invalid message %d type %d", i, msg[i]->msg_style);
          return PAM_CONV_ERR;
        }
    }

  *respHandle = resp;
  return PAM_SUCCESS;
}

int
main(int argc, char **argv)
{
  pam_handle_t *pamh;
  void *data;
  int code;

  struct pam_conv convData = {
    converse,
    data
  };

  LOG_INFO("before pam_start");
  code = pam_start("test_pam_rest", USER, &convData, &pamh);
  if (code != PAM_SUCCESS)
    {
      LOG_INFO("*** pam_start failed");
      pam_end(pamh, 0);
      exit(1);
    }
  
  LOG_INFO("before pam_authenticate");
  code = pam_authenticate(pamh, 0);
  if (code != PAM_SUCCESS)
    {
      LOG_INFO("*** pam_authenticate failed");
      pam_end(pamh, 0);
      exit(1);
    }

  const char *session = getenv(ENV_NAME);
  if (session == NULL)
    {
      LOG_INFO("*** pam_authenticate did not set a cookie");
      pam_end(pamh, 0);
      exit(1);
    }
  LOG_INFO("got cookie %s", session);

  LOG_INFO("before pam_acct_mgmt");
  code = pam_acct_mgmt(pamh, 0);
  if (code != PAM_SUCCESS)
    {
      LOG_INFO("*** pam_pam_acct_mgmt failed");
      pam_end(pamh, 0);
      exit(1);
    }

  LOG_INFO("all done.");
  pam_end(pamh, 0);

  printf("%s\n", session);
  exit(0);
}
