/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2017
 */

package com.ibm.streamsx.rest;

import java.io.IOException;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Exception for REST api wrappers
 */
public class RESTException extends IOException {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    private int status;
    private RESTErrorMessage error;

    /**
     * Customized exception that can provide more information on REST errors
     * 
     * @param code
     *            - error message code (matches HTTP response codes)
     *
     * @return a {@link RESTException} created from a code and an IBM Streams
     *         Message
     */
    public static final RESTException create(int code, String streamsMessage) {

        if (streamsMessage == null || streamsMessage.isEmpty()) {
            return new RESTException(code);
        } else {
            try {
                
                JsonObject jsonError = new JsonParser().parse(streamsMessage).getAsJsonObject();

                if (jsonError.has("message")) {

                    RESTErrorMessage error = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(streamsMessage,
                            RESTErrorMessage.class);
                    return new RESTException(code, error);
                } else if (jsonError.has("messages")) {
                    JsonArray messages = jsonError.getAsJsonArray("messages");
                    if (messages.size() == 1) {
                        RESTErrorMessage error = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(messages.get(0).getAsJsonObject(),
                                RESTErrorMessage.class);
                        return new RESTException(code, error);
                    }
                }
                
                return new RESTException(code, streamsMessage);
                
            } catch (JsonSyntaxException e) {
                // chances are this is a 404 http error with some other message
                return new RESTException(code, streamsMessage);
            }
        }
    }

    private RESTException(int code, RESTErrorMessage error) {
        super(error.getMessage());
        status = code;
        this.error = error;
    }

    /**
     * Customized exception that can provide more information on REST errors
     * 
     * @param code
     *            - error message code (currently will contain only HTTP
     *            response codes)
     */
    public RESTException(int code) {
        super("HTTP code:" + code);
        status = code;
    }

    /**
     * Customized exception that can provide more information on REST errors
     * 
     * @param code
     *            - error message code
     * @param message
     *            - error message to be seen
     */
    public RESTException(int code, String message) {
        super(message);
        status = code;
    }

    /**
     * Customized exception that can provide more information on REST errors
     * 
     * @param message
     *            - error message to be seen
     */
    public RESTException(String message) {
        super(message);
    }

    /**
     * Customized exception that can provide more information on REST errors
     * 
     * @param message
     *            - error message to be seen
     * @param exception
     *            - cause
     */
    public RESTException(String message, Exception exception) {
        super(message, exception);
        if (exception instanceof RESTException) {
            this.status = ((RESTException)exception).status;
        }
    }

    /**
     * Gets the error status code for this exception
     * 
     * @return the error status code
     */
    public int getStatusCode() {
        return status;
    }

    /**
     * Gets the IBM Streams message ID for this exception
     * 
     * @return the IBM Streams message ID
     */

    public String getStreamsErrorMessageId() {
        String id = null;
        if (error != null) {
            id = error.getMessageId();
        }
        return id;
    }

    /**
     * Gets the IBM Streams message for this exception as a Json Object
     * 
     * @return the IBM Streams message as a {@link JsonObject}
     */
    public JsonObject getStreamsErrorMessageAsJson() {
        JsonObject json;
        if (error != null) {
            json = error.getAsJson();
        } else {
            json = new JsonObject();
        }
        return json;
    }
}
