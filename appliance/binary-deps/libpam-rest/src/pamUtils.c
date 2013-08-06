/**********************************************************************
 *
 * pamUtils.c
 *
 **********************************************************************/

#include "bufUtils.h"
#include "pamUtils.h"

#ifdef HAVE_STRING_H
#  include <string.h>
#endif

#ifdef HAVE_UNISTD_H
#  include <unistd.h>
#endif

#ifdef HAVE_STDARG_H
#  include <stdarg.h>
#endif

#ifdef HAVE_STRING_H
#  include <string.h>
#endif

#ifdef HAVE_ALLOCA_H
#  include <alloca.h>
#endif

#include <json/json.h>
#include <json/json_object_private.h>

#include <syslog.h>

int
logAny(pam_handle_t *h, int prio, const char *msg)
{
  struct pam_message req0 = {
    prio,
    msg
  };
  struct pam_response resp0;

  const struct pam_message *req[] = { &req0 };
  struct pam_response *resp = NULL;
  
  struct pam_conv *conv;

  syslog(LOG_INFO, msg);

  conv = NULL;
  int code = pam_get_item(h, PAM_CONV, (const void **) &conv);
  if (code != PAM_SUCCESS)
    return code;

  code = conv->conv(1, req, &resp, conv->appdata_ptr);
  if (resp)
    {
      if (resp[0].resp)
        free(resp[0].resp);
      free(resp);
    }
  return code;

}

int
logInfo(pam_handle_t *h, const char *msg, ...)
{
  va_list ap;
  char *msgBuf;

  pam_rest_t *data = getModuleData(h);
  if ((data != NULL) && (data->silent == 0))
    return;

  va_start(ap, msg);
  vasprintf(&msgBuf, msg, ap);
  va_end(ap);

  int ret = logAny(h, PAM_TEXT_INFO, msgBuf);
  free(msgBuf);
  return ret;
}

int
logError(pam_handle_t *h, const char *msg, ...)
{
  va_list ap;
  char *msgBuf;

  pam_rest_t *data = getModuleData(h);
  if ((data != NULL) && (data->silent == 0))
    return;

  va_start(ap, msg);
  vasprintf(&msgBuf, msg, ap);
  va_end(ap);

  int ret = logAny(h, PAM_ERROR_MSG, msgBuf);
  free(msgBuf);
  return ret;
}

int
logDebug(pam_handle_t *h,
         const char *file, int line, const char *function,
         const char *msg, ...)
{
  va_list ap;
  char *dbgBuf;
  char *msgBuf;

  pam_rest_t *data = getModuleData(h);
  if ((data != NULL) && ((data->silent != 0) || (data->debug == 0)))
    return;

  asprintf(&dbgBuf, "%s:%d:%s: ", file, line, function);
  if (dbgBuf == NULL)
    return -1;

  va_start(ap, msg);
  vasprintf(&msgBuf, msg, ap);
  va_end(ap);
  
  if (msgBuf == NULL)
    return -1;

  char *logBuf = alloca(strlen(dbgBuf) + strlen(msgBuf) + 1);
  if (logBuf == NULL)
    return -1;

  strcpy(logBuf, dbgBuf);
  strcat(logBuf, msgBuf);

  int ret = logAny(h, PAM_TEXT_INFO, logBuf);

  free(msgBuf);
  
  return ret;
}

/* get or create the module-specific data */
pam_rest_t *
getModuleData(pam_handle_t *h)
{
  int code;
  pam_rest_t *data;

  /* FIXME: find an example to see if MODULENAME needs to be salted */
  code = pam_get_data(h, MODULENAME, (const void **) &data);
  if (code == PAM_SUCCESS)
    return data;
  if (code == PAM_SYSTEM_ERR)
    {
      fprintf(stderr,
              "%s:%s: failed to retrieve module data\n",
              __FILE__, __FUNCTION__);
      return NULL;
    }

  /* PAM_NO_MODULE_DATA */
  data = (pam_rest_t *) malloc(sizeof(pam_rest_t));
  memset(data, 0, sizeof(pam_rest_t));
  data->allowNullAuthtok = 1;
  data->curl = curl_easy_init();
  data->method = PAM_METHOD_POST;
  data->postType = PAM_TYPE_JSON;
  bufInit(&data->head);
  bufInit(&data->body);
  data->headerState = HEADER_START;

  code = pam_set_data(h, MODULENAME, (void *) data, cleanupModuleData);
  
  return data;
}

void
cleanupModuleData(pam_handle_t *h, void *rawData, int error_status)
{
  pam_rest_t *data = (pam_rest_t *) rawData;

  if (data->uri)
    free((void *) data->uri);
  /* session is part of the header buffer */
  if (data->curl)
    curl_easy_cleanup(data->curl);
  bufDestroy(&data->head);
  bufDestroy(&data->body);

  struct pam_header_list_t *p, *q;
  for (p = data->headers; p;)
    {
      q = p->next;
      free(p);
      p = q;
    }

  if (data->tokener != NULL)
    json_tokener_free(data->tokener);
  if (data->json != NULL)
    json_object_put(data->json);

  free(data);
}

int
parseOpts(pam_handle_t *h, int args, const char **argv)
{
  pam_rest_t *data;
  int i;

  data = getModuleData(h);
  if (!data)
    return -1;

  for (i = 0; i < args; ++i)
    {

      if (!strncmp(argv[i], "uri=", 4))
        {
          if (data->uri)
            free((void *) data->uri);
          data->uri = strdup(argv[i]+4);
          continue;
        }

      if (!strcmp(argv[i], "method=GET"))
        {
          data->method = PAM_METHOD_GET;
          continue;
        }

      if (!strcmp(argv[i], "method=POST"))
        {
          data->method = PAM_METHOD_POST;
          continue;
        }

      if (!strncmp(argv[i], "method=", 7))
        {
          PAM_LOG_ERROR(h, "invalid REST method '%s'", argv[i]+7);
          return -1;
        }

      if (!strcmp(argv[i], "post_type=form"))
        {
          data->postType = PAM_TYPE_FORM;
          continue;
        }
      if (!strcmp(argv[i], "post_type=json"))
        {
          data->postType = PAM_TYPE_JSON;
          continue;
        }
      if (!strncmp(argv[i], "post_type=", 10))
        {
          PAM_LOG_ERROR(h, "invalid POST type '%s'", argv[i]+10);
          return -1;
        }

      if (!strcmp(argv[i], "debug"))
        {
          data->debug = 1;
          continue;
        }

#if 0
      if (!strcmp(argv[i], "silent"))
        {
          data->silent = 1;
          continue;
        }
#endif

      PAM_LOG_ERROR(h, "invalid arguments");
      return -1;

    }

  return 0;
}

/* retrieve a copy of the password from the heap */

int
getPassword(pam_handle_t *h, char **pass)
{
  const void *item;
  char *passCopy;
  int code;

  pam_rest_t *data = getModuleData(h);

  /* try to re-use a non-empty authtok from another module */
  code = pam_get_item(h, PAM_AUTHTOK, &item);
  if (code == PAM_SUCCESS)
    {
      if (item && strlen(item))
        {
          passCopy = strdup(item);
          if (passCopy)
            {
              *pass = passCopy;
              PAM_LOG_DEBUG(h, "re-using authtok \"%s\"", *pass);
              return PAM_SUCCESS;
            }
          PAM_LOG_ERROR(h, "cannot copy authtok");
          return PAM_BUF_ERR;
        }
    }

  /* else, use the conversation function to prompt for a password */

  struct pam_message req0 = {
    PAM_PROMPT_ECHO_OFF,
    "Password: "
  };
  struct pam_response resp0;

  const struct pam_message *req[] = { &req0 };
  struct pam_response *resp = NULL;
  
  struct pam_conv *conv;

  code = pam_get_item(h, PAM_CONV, (const void **) &conv);
  if (code != PAM_SUCCESS)
    {
      PAM_LOG_DEBUG(h, "unable to start password conversation");
      return code;
    }

  code = conv->conv(1, req, &resp, conv->appdata_ptr);
  /* application should not have set resp */
  if (code != PAM_SUCCESS)
    {
      PAM_LOG_DEBUG(h, "password conversation failed");
      return code;
    }

  /* claims to have a password, does not */
  if (resp == NULL)
    {
      PAM_LOG_DEBUG(h, "invalid password conversation results");
      return PAM_CONV_ERR;
    }

  /* still need to free the password eventually */
  if (resp[0].resp && strlen(resp[0].resp))
    {
      *pass = resp[0].resp;
      free(resp);
      PAM_LOG_DEBUG(h, "obtained new authtok \"%s\"", *pass);
      return PAM_SUCCESS;
    }

  PAM_LOG_INFO(h, "password conversation return an empty result");
  free(resp);
  if (data->allowNullAuthtok)
    {
      *pass = strdup("");
      return PAM_SUCCESS;
    }
  else
    return PAM_CONV_ERR;
  
}

int
getPort(pam_handle_t *h, char **port)
{
  int code;
  char *buf;

  code = pam_get_item(h, PAM_TTY, (void *) &buf);
  if ((code != PAM_SUCCESS)
      || (buf == NULL)
      || (buf[0] == '\0'))
    {
      buf = ttyname(STDIN_FILENO);
      if ((buf == NULL)
          || (buf[0] == '\0'))
        buf = "unknown";
    }

  if (!strncmp(buf, "/dev/", 5))
    buf += 5;

  *port = buf;
  return PAM_SUCCESS;

}

int
getHost(pam_handle_t *h, char **host)
{
  int code;
  char *buf;

  code = pam_get_item(h, PAM_RHOST, (void *) &buf);
  if ((code != PAM_SUCCESS)
      || (buf == NULL)
      || (buf[0] == '\0'))
    buf = "localhost";

  *host = buf;
  return PAM_SUCCESS;

}

size_t
onCurlWrite(void *buf, size_t size, size_t nmemb, void *userData)
{
  pam_handle_t *h = (pam_handle_t *) userData;
  pam_rest_t *data = getModuleData(h);

  char *start = data->body.end;
  size_t sz = bufAppendSubstring(&data->body, (char *) buf, size*nmemb);
  if (sz != size*nmemb)
    return sz;
  char *end = data->body.end;
  if (start == NULL)
    start = data->body.start;

  PAM_LOG_DEBUG(h, "received %d bytes from body: <<%s>>", size*nmemb, start);

  return sz;
}

/* called for each complete header */

size_t
onCurlHeader(void *buf, size_t size, size_t nmemb, void *userData)
{
  pam_handle_t *h = (pam_handle_t *) userData;
  pam_rest_t *data = getModuleData(h);

  PAM_LOG_DEBUG(h, "received %d bytes from head", size*nmemb);

  char *start = data->head.end;
  size_t sz = bufAppendSubstring(&data->head, (char *) buf, size*nmemb);
  if (sz != size*nmemb)
    {
      PAM_LOG_ERROR(h, "received %d bytes, saved %d bytes",
               size*nmemb, sz);
      return sz;
    }
  char *end = data->head.end;
  if (start == NULL)
    start = data->head.start;

  while ((start < end) && isspace(*(end-1)))
    {
      --end;
      *end = '\0';
    }

  if (data->headerState == HEADER_START)
    {
      PAM_LOG_DEBUG(h, "received HTTP response: %s", start);
      data->headerState = HEADER_HEADERS;
      return sz;
    }

  if (data->headerState == HEADER_HEADERS)
    {
      if (start == end)
        {
          PAM_LOG_DEBUG(h, "reached end of headers");
          data->headerState = HEADER_END;
          return sz;
        }

      /* else, process this header */
      /* try to parse a key and value */
      char *colon = strstr(start, ":");
      if (colon > start)
        {
          /* trim the field value of whitespace */
          while ((colon < end) && isspace(*colon))
            ++colon;
                 
          *colon = '\0';
          PAM_LOG_DEBUG(h, "found header <<%s>> <<%s>>", start, colon+2);
          struct pam_header_list_t *el = malloc(sizeof(struct pam_header_list_t));
          el->key = start;
          el->val = colon+2;
          el->next = data->headers;
          data->headers = el;
        }
      else
        {
          PAM_LOG_INFO(h, "unparsable header <<%s>>", start);
        }
      return sz;
    }

  if (data->headerState == HEADER_END)
    {
      if (end > start)
        {
          PAM_LOG_INFO(h, "unparsable header after end <<%s>>", start);
        }
      return sz;
    }

  PAM_LOG_INFO(h, "unparsable header in invalid state <<%s>>", start);
  return sz;

}

int
getSession(pam_handle_t *h,
           const char *user, const char *pass,
           const char *host, const char *port)
{

  /* make sure the module data are properly initialized */
  pam_rest_t *data = getModuleData(h);
  if (data == NULL)
    {
      PAM_LOG_ERROR(h, "NULL module data");
      return PAM_AUTHINFO_UNAVAIL;
    }
  if (data->uri == NULL)
    {
      PAM_LOG_ERROR(h, "NULL uri");
      return PAM_AUTHINFO_UNAVAIL;
    }
  if (data->uri == NULL)
    {
      PAM_LOG_ERROR(h, "NULL curl state");
      return PAM_AUTHINFO_UNAVAIL;
    }

  CURLcode ccode;

#if 0
  /* enable here to get the response line too */
  ccode = curl_easy_setopt(data->curl, CURLOPT_HEADER, 1);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;
#endif
  ccode = curl_easy_setopt(data->curl, CURLOPT_HTTPGET, 1);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;
  ccode = curl_easy_setopt(data->curl, CURLOPT_WRITEFUNCTION, onCurlWrite);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;
  ccode = curl_easy_setopt(data->curl, CURLOPT_HEADERFUNCTION, onCurlHeader);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;
  ccode = curl_easy_setopt(data->curl, CURLOPT_WRITEDATA, (void *) h);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;
  ccode = curl_easy_setopt(data->curl, CURLOPT_WRITEHEADER, (void *) h);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;

  /* no additional headers for GET */

  /*
   * construct GET parameters
   * Here we put user and pass in the query args
   * instead of a basic-auth request;
   * we have more parameters anyway
   */
  
  pam_buf_t uri;
  char *parm;

  bufInit(&uri);
  bufAppend(&uri, data->uri);

  bufAppend(&uri, "?");

  parm = curl_escape(user, 0);
  bufAppend(&uri, "user=");
  bufAppend(&uri, parm);
  curl_free(parm);

  bufAppend(&uri, "&");

  parm = curl_escape(pass, 0);
  bufAppend(&uri, "password=");
  bufAppend(&uri, parm);
  curl_free(parm);

  bufAppend(&uri, "&");

  parm = curl_escape(host, 0);
  bufAppend(&uri, "host=");
  bufAppend(&uri, parm);
  curl_free(parm);

  bufAppend(&uri, "&");

  parm = curl_escape(port, 0);
  bufAppend(&uri, "port=");
  bufAppend(&uri, parm);
  curl_free(parm);

  PAM_LOG_DEBUG(h, "visiting uri %s", uri.start);

  ccode = curl_easy_setopt(data->curl, CURLOPT_URL, uri.start);
  if (ccode != CURLE_OK)
    {
      PAM_LOG_ERROR(h, "failed to set url url");
      bufDestroy(&uri);
      return PAM_AUTHINFO_UNAVAIL;
    }

  /*
   * 8. invoke request
   */

  int code = performSession(h);
  bufDestroy(&uri);
  return code;

}

int
postSessionForm(pam_handle_t *h,
                const char *user, const char *pass,
                const char *host, const char *port)
{

  /* make sure the module data are properly initialized */
  pam_rest_t *data = getModuleData(h);
  if (data == NULL)
    {
      PAM_LOG_ERROR(h, "NULL module data");
      return PAM_AUTHINFO_UNAVAIL;
    }
  if (data->uri == NULL)
    {
      PAM_LOG_ERROR(h, "NULL uri");
      return PAM_AUTH_ERR;
    }
  if (data->uri == NULL)
    {
      PAM_LOG_ERROR(h, "NULL curl state");
      return PAM_AUTHINFO_UNAVAIL;
    }

  CURLcode ccode;

#if 0
  /* enable here to get the response line too */
  ccode = curl_easy_setopt(data->curl, CURLOPT_HEADER, 1);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;
#endif
  ccode = curl_easy_setopt(data->curl, CURLOPT_POST, 1);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;
  ccode = curl_easy_setopt(data->curl, CURLOPT_WRITEFUNCTION, onCurlWrite);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;
  ccode = curl_easy_setopt(data->curl, CURLOPT_HEADERFUNCTION, onCurlHeader);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;
  ccode = curl_easy_setopt(data->curl, CURLOPT_WRITEDATA, (void *) h);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;
  ccode = curl_easy_setopt(data->curl, CURLOPT_WRITEHEADER, (void *) h);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;

  /* extra headers not required for POST */

  /*
   * construct POST parameters
   */

  pam_buf_t form;
  /* Remember to bufDestroy() this in all cases */

  char *parm;

  bufInit(&form);

  parm = curl_escape(user, 0);
  bufAppend(&form, "user=");
  bufAppend(&form, parm);
  curl_free(parm);

  bufAppend(&form, "&");

  parm = curl_escape(pass, 0);
  bufAppend(&form, "password=");
  bufAppend(&form, parm);
  curl_free(parm);

  bufAppend(&form, "&");

  parm = curl_escape(host, 0);
  bufAppend(&form, "host=");
  bufAppend(&form, parm);
  curl_free(parm);

  bufAppend(&form, "&");

  parm = curl_escape(port, 0);
  bufAppend(&form, "port=");
  bufAppend(&form, parm);
  curl_free(parm);

  PAM_LOG_DEBUG(h, "posting to uri %s", data->uri);
  PAM_LOG_DEBUG(h, "posting data: %s", form.start);

  ccode = curl_easy_setopt(data->curl, CURLOPT_URL, data->uri);
  if (ccode != CURLE_OK)
    {
      PAM_LOG_ERROR(h, "failed to set url url");
      bufDestroy(&form);
      return PAM_AUTHINFO_UNAVAIL;
    }

  ccode = curl_easy_setopt(data->curl, CURLOPT_POSTFIELDS, form.start);
  if (ccode != CURLE_OK)
    {
      PAM_LOG_ERROR(h, "failed to set post data");
      bufDestroy(&form);
      return PAM_AUTHINFO_UNAVAIL;
    }

  ccode = curl_easy_setopt(data->curl, CURLOPT_POSTFIELDSIZE, form.end-form.start);
  if (ccode != CURLE_OK)
    {
      PAM_LOG_ERROR(h, "failed to set post data size");
      bufDestroy(&form);
      return PAM_AUTHINFO_UNAVAIL;
    }

  /*
   * 8. invoke request
   */

  int code = performSession(h);
  bufDestroy(&form);
  return code;
}

int
postSessionJson(pam_handle_t *h,
                const char *user, const char *pass,
                const char *host, const char *port)
{

  /* make sure the module data are properly initialized */
  pam_rest_t *data = getModuleData(h);
  if (data == NULL)
    {
      PAM_LOG_ERROR(h, "NULL module data");
      return PAM_AUTHINFO_UNAVAIL;
    }
  if (data->uri == NULL)
    {
      PAM_LOG_ERROR(h, "NULL uri");
      return PAM_AUTH_ERR;
    }
  if (data->uri == NULL)
    {
      PAM_LOG_ERROR(h, "NULL curl state");
      return PAM_AUTHINFO_UNAVAIL;
    }

  CURLcode ccode;

#if 0
  /* enable here to get the response line too */
  ccode = curl_easy_setopt(data->curl, CURLOPT_HEADER, 1);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;
#endif
  ccode = curl_easy_setopt(data->curl, CURLOPT_POST, 1);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;
  ccode = curl_easy_setopt(data->curl, CURLOPT_WRITEFUNCTION, onCurlWrite);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;
  ccode = curl_easy_setopt(data->curl, CURLOPT_HEADERFUNCTION, onCurlHeader);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;
  ccode = curl_easy_setopt(data->curl, CURLOPT_WRITEDATA, (void *) h);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;
  ccode = curl_easy_setopt(data->curl, CURLOPT_WRITEHEADER, (void *) h);
  if (ccode != CURLE_OK)
    return PAM_AUTHINFO_UNAVAIL;

  /*
   * construct the form data as application/json
   */

  /* construct a curl_slist of new headers including Content-Type */

  struct curl_slist *newHeaders = NULL;
  struct curl_slist *prev;
  /* FIXME: make sure to free this in all cases with curl_slist_free_all() */

  prev = curl_slist_append(newHeaders, "Content-Type: application/json");
  if (prev == NULL)
    {
      if (newHeaders != NULL)
        curl_slist_free_all(newHeaders);
      return PAM_AUTHINFO_UNAVAIL;
    }
  newHeaders = prev;

  ccode = curl_easy_setopt(data->curl, CURLOPT_HTTPHEADER, (void *) newHeaders);
  if (ccode != CURLE_OK)
    {
      curl_slist_free_all(newHeaders);
      return PAM_AUTHINFO_UNAVAIL;
    }

  /*
   * construct POST parameters
   */

  struct json_object *json = json_object_new_object();
  /* remember to free/deallocate this with json_object_put() */

  char *parm;

  parm = curl_escape(user, 0);
  json_object_object_add(json,
                         "user",
                         json_object_new_string(parm));
  curl_free(parm);

  parm = curl_escape(pass, 0);
  json_object_object_add(json,
                         "password",
                         json_object_new_string(parm));
  curl_free(parm);

  parm = curl_escape(host, 0);
  json_object_object_add(json,
                         "host",
                         json_object_new_string(parm));
  curl_free(parm);

  parm = curl_escape(port, 0);
  json_object_object_add(json,
                         "port",
                         json_object_new_string(parm));
  curl_free(parm);

  PAM_LOG_DEBUG(h, "posting to uri %s", data->uri);

  ccode = curl_easy_setopt(data->curl, CURLOPT_URL, data->uri);
  if (ccode != CURLE_OK)
    {
      PAM_LOG_ERROR(h, "failed to set url url");
      curl_slist_free_all(newHeaders);
      json_object_put(json);
      return PAM_AUTHINFO_UNAVAIL;
    }

  /* serialize json for posting */
  const char *jsonString = json_object_get_string(json);
  PAM_LOG_DEBUG(h, "posting %d bytes of json %s",
                strlen(jsonString), jsonString);

  /* managed by the json object, will be freed automatically */

  ccode = curl_easy_setopt(data->curl, CURLOPT_POSTFIELDS, jsonString);
  if (ccode != CURLE_OK)
    {
      PAM_LOG_ERROR(h, "failed to set post data");
      curl_slist_free_all(newHeaders);
      json_object_put(json);
      return PAM_AUTHINFO_UNAVAIL;
    }

  ccode = curl_easy_setopt(data->curl, CURLOPT_POSTFIELDSIZE, strlen(jsonString));
  if (ccode != CURLE_OK)
    {
      PAM_LOG_ERROR(h, "failed to set post data size");
      curl_slist_free_all(newHeaders);
      json_object_put(json);
      return PAM_AUTHINFO_UNAVAIL;
    }

  /*
   * 8. invoke request
   */

  int code = performSession(h);
  curl_slist_free_all(newHeaders);
  json_object_put(json);
  return code;
}

int
performSession(pam_handle_t *h)
{

  pam_rest_t *data = getModuleData(h);

  /* reset any head or body data from a previous post */
  data->head.end = data->head.start;
  data->body.end = data->body.start;

  CURLcode ccode = curl_easy_perform(data->curl);
  switch (ccode)
    {
      
    case CURLE_OK:
      return PAM_SUCCESS;
      break;
      
    case CURLE_REMOTE_ACCESS_DENIED:
      /* maybe PAM_USER_UNKNOWN */
      PAM_LOG_ERROR(h, "curl returned access denied");
      return PAM_CRED_INSUFFICIENT;

    case CURLE_ABORTED_BY_CALLBACK:
      PAM_LOG_ERROR(h, "curl callback terminated");
      return PAM_CRED_INSUFFICIENT;

    case CURLE_COULDNT_RESOLVE_PROXY:
    case CURLE_COULDNT_RESOLVE_HOST:
    case CURLE_COULDNT_CONNECT:
      PAM_LOG_ERROR(h, "curl could not connect");
      return PAM_AUTHINFO_UNAVAIL;
      
    case CURLE_GOT_NOTHING:
      PAM_LOG_ERROR(h, "curl received empty response");
      return PAM_AUTHINFO_UNAVAIL;

    default:
      PAM_LOG_ERROR(h, "invalid curl response %d", ccode);
      return PAM_AUTHINFO_UNAVAIL;

    }

}

int
parseResponse(pam_handle_t *h)
{

  pam_rest_t *data = getModuleData(h);

  long hcode = 0;
  CURLcode ccode = curl_easy_getinfo (data->curl, CURLINFO_RESPONSE_CODE, &hcode);
  if (ccode != CURLE_OK)
    {
      PAM_LOG_ERROR(h, "cannot get HTTP response code");
      return PAM_AUTHINFO_UNAVAIL;
    }

  if (hcode == 401)
    {
      PAM_LOG_ERROR(h, "authorization denied (REST code %d)", hcode);
      return PAM_AUTH_ERR;
    }
  if (hcode != 200)
    {
      PAM_LOG_ERROR(h, "invalid HTTP response code %d", hcode);
      return PAM_AUTHINFO_UNAVAIL;
    }

  return PAM_SUCCESS;

}

int
parseHeaders(pam_handle_t *h)
{
  pam_rest_t *data = getModuleData(h);

  /* no headers, therefore no cookie */
  if (data->head.start == NULL)
    {
      PAM_LOG_ERROR(h, "no headers returned");
      return PAM_AUTHINFO_UNAVAIL;
    }

  struct pam_header_list_t *headers;
  size_t sz = strlen(COOKIE_NAME);
  for (headers = data->headers; headers; headers = headers->next)
    {
      if (strcmp(headers->key, "Set-Cookie"))
        continue;
      if (strncmp(headers->val, COOKIE_NAME, sz))
        continue;
      if (headers->val[sz] != '=')
        continue;

      /* ignore the other key/val pairs in the cookie */
      char *semi = strchr(headers->val, ';');
      if (semi)
        *semi = '\0';

      data->session = headers->val+sz+1;
      break;
    }

  if (data->session == NULL)
    {
      PAM_LOG_INFO(h, "no session cookie, returning PAM_AUTH_ERR");
      return PAM_AUTH_ERR;
    }

  return PAM_SUCCESS;

}

int
parseJson(pam_handle_t *h)
{
  pam_rest_t *data = getModuleData(h);

  /*
   * 9. parse YAML response and cookie
   */

  /* no json data */
  if (data->body.start == NULL)
    {
      return PAM_SUCCESS;
    }

  /* parse out any json text */
  data->tokener = json_tokener_new();
  if (data->tokener == NULL)
    {
      PAM_LOG_ERROR(h, "cannot create JSON tokener");
      return PAM_AUTH_ERR;
    }

  PAM_LOG_DEBUG(h, "parsing JSON: %s", data->body.start);
  data->json = json_tokener_parse_ex(data->tokener,
                                     data->body.start,
                                     (data->body.end - data->body.start));
#if HAVE_JSON_C_10
  enum json_tokener_error jcode = json_tokener_get_error(data->tokener);
#else
  ptrdiff_t jcode = data->tokener->err;
#endif
  if (jcode == json_tokener_continue)
    {
      PAM_LOG_ERROR(h, "incomplete JSON response");
      return PAM_AUTH_ERR;
    }
  if (jcode != json_tokener_success)
    {
#if HAVE_JSON_C_10
      PAM_LOG_ERROR(h, "invalid JSON response: %s",
                json_tokener_error_desc(jcode));
#else
      PAM_LOG_ERROR(h, "invalid JSON response: %s",
                json_tokener_errors[jcode]);
#endif
      return PAM_AUTH_ERR;
    }
  if (data->json == NULL)
    {
      PAM_LOG_ERROR(h, "cannot parse JSON response (NULL object)");
      return PAM_AUTH_ERR;
    }

  /* if it's a json dict, we can parse it */
  if (json_object_get_type(data->json) != json_type_object)
    {
      PAM_LOG_ERROR(h, "unparsable JSON response (not object)");
      return PAM_AUTH_ERR;
    }
  
  /*
   * try to parse the keys in this json object
   * 'success' --> response code
   * 'message' --> console message
   * 'error_message' --> error message
   */
  
  json_object_iter iter;
  int pcode = PAM_SUCCESS;
  json_object_object_foreachC(data->json, iter)
    {
      if (!strcmp(iter.key, "message"))
        {
          if (json_object_get_type(iter.val) == json_type_string)
            {
              const char *msg =json_object_get_string(iter.val);
              if (strlen(msg))
                PAM_LOG_INFO(h, msg);
            }
          else if (json_object_get_type(iter.val) != json_type_null)
            {
              PAM_LOG_ERROR(h, "invalid REST message (not a string or null)");
              pcode = PAM_AUTH_ERR;
            }
        }
      if (!strcmp(iter.key, "error_message"))
        {
          if (json_object_get_type(iter.val) == json_type_string)
            {
              const char *msg =json_object_get_string(iter.val);
              if (strlen(msg))
                PAM_LOG_ERROR(h, msg);
            }
          else if (json_object_get_type(iter.val) != json_type_null)
            {
              PAM_LOG_ERROR(h, "invalid REST error message (not a string or null)");
              pcode = PAM_AUTH_ERR;
            }
        }
      if (!strcmp(iter.key, "success"))
        {
          if (json_object_get_type(iter.val) == json_type_boolean)
            {
              boolean rcode = json_object_get_boolean(iter.val);
              if (!rcode)
                {
                  PAM_LOG_ERROR(h, "REST code %d", rcode);
                  pcode = PAM_AUTH_ERR;
                }
            }
          else
            {
              PAM_LOG_ERROR(h, "invalid REST code (not boolean)");
              pcode = PAM_AUTH_ERR;
            }
        }
      if (!strcmp(iter.key, REST_NAME))
        {
          if (json_object_get_type(iter.val) == json_type_string)
            {
              const char *session = json_object_get_string(iter.val);
              if (!session)
                {
                  PAM_LOG_ERROR(h, "invalid (NULL) REST session");
                  pcode = PAM_AUTH_ERR;
                }
              else if (data->session == NULL)
                {
                  data->session = session;
                  PAM_LOG_DEBUG(h, "retrieved session from json: %s", session);
                }
              else if (strcmp(session, data->session))
                {
                  PAM_LOG_ERROR(h, "mismatched REST session %s vs %s",
                            session, data->session);
                  pcode = PAM_AUTH_ERR;
                }
            }
          else if (json_object_get_type(iter.val) == json_type_null)
            {
              PAM_LOG_ERROR(h, "invalid REST session (not string or null)");
              pcode = PAM_AUTH_ERR;
            }
        }
    }

  return pcode;

}
