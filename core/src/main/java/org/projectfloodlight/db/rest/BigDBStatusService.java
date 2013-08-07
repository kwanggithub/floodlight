package org.projectfloodlight.db.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.rest.auth.AuthenticationRequiredException;
import org.projectfloodlight.db.rest.auth.BadLoginException;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.data.Preference;
import org.restlet.data.Status;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.service.StatusService;

public class BigDBStatusService extends StatusService {

    public BigDBStatusService() {
    }

    public BigDBStatusService(boolean enabled) {
        super(enabled);
    }
    
    static Map<BigDBException.Type, Status> statusMap;
            
    
    static {
        statusMap = new HashMap<BigDBException.Type, Status>();
        statusMap.put(BigDBException.Type.MULTIPLE_CHOICES, Status.REDIRECTION_MULTIPLE_CHOICES);
        statusMap.put(BigDBException.Type.MOVED_PERMANENTLY, Status.REDIRECTION_PERMANENT);
        statusMap.put(BigDBException.Type.SEE_OTHER, Status.REDIRECTION_SEE_OTHER);
        statusMap.put(BigDBException.Type.NOT_MODIFIED, Status.REDIRECTION_NOT_MODIFIED);
        statusMap.put(BigDBException.Type.TEMPORARY_REDIRECT, Status.REDIRECTION_TEMPORARY);
        statusMap.put(BigDBException.Type.BAD_CLIENT_REQUEST, Status.CLIENT_ERROR_BAD_REQUEST);
        statusMap.put(BigDBException.Type.UNAUTHORIZED, Status.CLIENT_ERROR_UNAUTHORIZED);
        statusMap.put(BigDBException.Type.FORBIDDEN, Status.CLIENT_ERROR_FORBIDDEN);
        statusMap.put(BigDBException.Type.NOT_FOUND, Status.CLIENT_ERROR_NOT_FOUND);
        statusMap.put(BigDBException.Type.METHOD_NOT_ALLOWED, Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
        statusMap.put(BigDBException.Type.NOT_ACCEPTABLE, Status.CLIENT_ERROR_NOT_ACCEPTABLE);
        statusMap.put(BigDBException.Type.REQUEST_TIMEOUT, Status.CLIENT_ERROR_REQUEST_TIMEOUT);
        statusMap.put(BigDBException.Type.CONFLICT, Status.CLIENT_ERROR_CONFLICT);
        statusMap.put(BigDBException.Type.GONE, Status.CLIENT_ERROR_GONE);
        statusMap.put(BigDBException.Type.REQUEST_ENTITY_TOO_LARGE, Status.CLIENT_ERROR_REQUEST_ENTITY_TOO_LARGE);
        statusMap.put(BigDBException.Type.UNSUPPORTED_MEDIA_TYPE, Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE);
        statusMap.put(BigDBException.Type.REQUESTED_RANGE_NOT_SATISFIABLE, Status.CLIENT_ERROR_REQUESTED_RANGE_NOT_SATISFIABLE);
        statusMap.put(BigDBException.Type.INTERNAL_SERVER_ERROR, Status.SERVER_ERROR_INTERNAL);
        statusMap.put(BigDBException.Type.NOT_IMPLEMENTED, Status.SERVER_ERROR_NOT_IMPLEMENTED);
        statusMap.put(BigDBException.Type.SERVICE_UNAVAILABLE, Status.SERVER_ERROR_SERVICE_UNAVAILABLE);
    }
    
    public static Status mapErrorTypeToStatus(BigDBException.Type errorType) {
        Status status = statusMap.get(errorType);
        if (status == null)
            status = Status.SERVER_ERROR_INTERNAL;
        return status;
    }
    
    @Override
    public Status getStatus(Throwable throwable, Request request, Response response) {
        // BigDBExceptions
        if (throwable instanceof ResourceException) {
            Throwable cause = throwable.getCause();
            if (cause instanceof AuthenticationRequiredException) {
                return new Status(Status.CLIENT_ERROR_UNAUTHORIZED, cause.getMessage());
            } else if (cause instanceof BadLoginException) {
                return new Status(Status.CLIENT_ERROR_UNAUTHORIZED, cause.getMessage());
            } else if (cause instanceof BigDBException) {
                BigDBException bigDBException = (BigDBException) cause;
                Status baseStatus = mapErrorTypeToStatus(bigDBException.getErrorType());
                Status status = new Status(baseStatus, cause.getMessage());
                return status;
            } else {
                return super.getStatus(throwable, request, response);
            }
        } else {
            return super.getStatus(throwable, request, response);
        }
    }
    
    public static class ErrorInfo {
        
        protected int errorCode;
        protected String description;
        
        public ErrorInfo(int errorCode, String description) {
            this.errorCode = errorCode;
            this.description = description;
        }
        
        @JsonProperty("error-code")
        public int getErrorCode() {
            return errorCode;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    @Override
    public Representation getRepresentation(Status status, Request request,
            Response response) {
        ErrorInfo errorInfo = new ErrorInfo(status.getCode(),
                status.getDescription());
        
        // Try to format the error result in the format requested by the user
        // in the accept header
        List<Preference<MediaType>> acceptedMediaTypeList =
                request.getClientInfo().getAcceptedMediaTypes();
        for (Preference<MediaType> acceptedMediaType: acceptedMediaTypeList) {
            MediaType mediaType = acceptedMediaType.getMetadata();
            if (mediaType.equals(MediaType.APPLICATION_JSON)) {
                return new JacksonRepresentation<ErrorInfo>(errorInfo);
            }
//            else if (mediaType.equals(MediaType.APPLICATION_XML) ||
//                    mediaType.equals(MediaType.TEXT_XML)) {
//                // TODO: Generate XML representation
//            }
        }

        // Return default representation if there was no accept header of it
        // we don't know how to handle what was specified.
        // FIXME: Should we include some additional error info if an accept
        // header was specified but it was not recognized.
        // Currently I don't think we handle this correctly.
        // But really we should handle that at the point we dispatch to a
        // method to service the request, not here.
        return new JacksonRepresentation<ErrorInfo>(errorInfo);
    }                
}
