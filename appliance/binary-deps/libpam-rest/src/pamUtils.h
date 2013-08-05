/**********************************************************************
 *
 * pamUtils.h
 *
 **********************************************************************/

#ifndef PAMUTILS_H
#define PAMUTILS_H

#include <curl/curl.h>
#include <security/pam_modules.h>
#include <json/json.h>

#define MODULENAME "pam_rest_data"
/* FIXME: not sure if this needs to be salted */

struct pam_header_list_t {
  struct pam_header_list_t *next;
  const char *key;
  const char *val;
};

enum pam_header_state_t {
  HEADER_START,
  HEADER_HEADERS,
  HEADER_END
};

enum pam_method_t {
  PAM_METHOD_GET,
  PAM_METHOD_POST,
  PAM_METHOD_PUT
};

enum pam_type_t {
  PAM_TYPE_FORM,
  PAM_TYPE_JSON
};

typedef struct {
  int debug;
  int silent;
  int allowNullAuthtok;
  int failureWarned;
  const char *uri;
  const char *session;
  CURL *curl;
  enum pam_method_t method;
  enum pam_type_t postType;

  struct pam_header_list_t *headers;
  enum pam_header_state_t headerState;
  pam_buf_t head;
  pam_buf_t body;
  
  struct json_tokener *tokener;
  struct json_object *json;

} pam_rest_t;

pam_rest_t *
getModuleData(pam_handle_t *h);

void
cleanupModuleData(pam_handle_t *h, void *data, int error_status);

extern int
logAny(pam_handle_t *h, int prio, const char *msg);

extern int
logInfo(pam_handle_t *h, const char *msg, ...);

#define PAM_LOG_INFO(h, msg, ...)  logInfo(h, msg, ##__VA_ARGS__)

extern int
logError(pam_handle_t *h, const char *msg, ...);

#define PAM_LOG_ERROR(h, msg, ...)  logError(h, msg, ##__VA_ARGS__)

extern int
logDebug(pam_handle_t *h,
         const char *file, int line, const char *function,
         const char *msg, ...);

#define PAM_LOG_DEBUG(h, msg, ...)  logDebug(h, __FILE__, __LINE__, __FUNCTION__, msg, ##__VA_ARGS__)

extern int
parseOpts(pam_handle_t *h, int args, const char **argv);

extern int
getPassword(pam_handle_t *h, char **pass);

extern int
getPort(pam_handle_t *h, char **port);

extern int
getHost(pam_handle_t *h, char **host);

extern size_t
onCurlWrite(void *buf, size_t size, size_t nmemb, void *data);

extern size_t
onCurlHeader(void *buf, size_t size, size_t nmemb, void *data);

extern int
getSession(pam_handle_t *h,
           const char *user, const char *pass,
           const char *host, const char *port);

extern int
postSessionForm(pam_handle_t *h,
                const char *user, const char *pass,
                const char *host, const char *port);

extern int
postSessionJson(pam_handle_t *h,
                const char *user, const char *pass,
                const char *host, const char *port);

extern int
performSession(pam_handle_t *h);

extern int
parseResponse(pam_handle_t *h);

extern int
parseHeaders(pam_handle_t *h);

extern int
parseJson(pam_handle_t *h);

#endif /* PAMUTILS_H */
