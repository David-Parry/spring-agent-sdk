/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * Global exception handler for the application.
 * Implements RFC-9457 Problem Details for HTTP APIs.
 * Provides centralized error handling with consistent error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Creates a new GlobalExceptionHandler.
     */
    public GlobalExceptionHandler() {
    }
    
    /**
     * Handles generic exceptions.
     *
     * @param ex      the thrown exception
     * @param request the current web request
     * @return a standardized problem detail describing the error
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, WebRequest request) {
        String correlationId = UUID.randomUUID().toString();
        String requestUrl = getRequestUrl(request);
        
        logger.error("Unhandled exception [correlationId={}, url={}]: {}", correlationId, requestUrl, ex.getMessage(), ex);
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please contact support with the correlation ID."
        );
        
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create("urn:qodo:command:errors:internal-server-error"));
        problemDetail.setProperty("correlationId", correlationId);
        problemDetail.setProperty("requestUrl", requestUrl);
        problemDetail.setProperty("timestamp", Instant.now().toString());
        
        return problemDetail;
    }
    
    /**
     * Handles IllegalArgumentException.
     *
     * @param ex      the thrown IllegalArgumentException
     * @param request the current web request
     * @return a problem detail describing the validation error
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        String correlationId = UUID.randomUUID().toString();
        String requestUrl = getRequestUrl(request);
        
        logger.warn("Invalid argument [correlationId={}, url={}]: {}", correlationId, requestUrl, ex.getMessage());
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.getMessage()
        );
        
        problemDetail.setTitle("Invalid Request");
        problemDetail.setType(URI.create("urn:qodo:command:errors:invalid-argument"));
        problemDetail.setProperty("correlationId", correlationId);
        problemDetail.setProperty("requestUrl", requestUrl);
        problemDetail.setProperty("timestamp", Instant.now().toString());
        
        return problemDetail;
    }
    
    /**
     * Handles IOException.
     *
     * @param ex      the thrown IOException
     * @param request the current web request
     * @return a problem detail describing the IO error
     */
    @ExceptionHandler(IOException.class)
    public ProblemDetail handleIOException(IOException ex, WebRequest request) {
        String correlationId = UUID.randomUUID().toString();
        String requestUrl = getRequestUrl(request);
        
        logger.error("IO error [correlationId={}, url={}]: {}", correlationId, requestUrl, ex.getMessage(), ex);
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An error occurred while processing the request."
        );
        
        problemDetail.setTitle("IO Error");
        problemDetail.setType(URI.create("urn:qodo:command:errors:io-error"));
        problemDetail.setProperty("correlationId", correlationId);
        problemDetail.setProperty("requestUrl", requestUrl);
        problemDetail.setProperty("timestamp", Instant.now().toString());
        
        return problemDetail;
    }
    
    /**
     * Handles JsonProcessingException.
     *
     * @param ex      the thrown JsonProcessingException
     * @param request the current web request
     * @return a problem detail describing the JSON parsing error
     */
    @ExceptionHandler(JsonProcessingException.class)
    public ProblemDetail handleJsonProcessingException(JsonProcessingException ex, WebRequest request) {
        String correlationId = UUID.randomUUID().toString();
        String requestUrl = getRequestUrl(request);
        
        logger.error("JSON processing error [correlationId={}, url={}]: {}", correlationId, requestUrl, ex.getMessage(), ex);
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Invalid JSON format in request."
        );
        
        problemDetail.setTitle("JSON Processing Error");
        problemDetail.setType(URI.create("urn:qodo:command:errors:json-processing-error"));
        problemDetail.setProperty("correlationId", correlationId);
        problemDetail.setProperty("requestUrl", requestUrl);
        problemDetail.setProperty("timestamp", Instant.now().toString());
        
        return problemDetail;
    }
    
    /**
     * Handles NullPointerException.
     *
     * @param ex      the thrown NullPointerException
     * @param request the current web request
     * @return a problem detail describing the null reference error
     */
    @ExceptionHandler(NullPointerException.class)
    public ProblemDetail handleNullPointerException(NullPointerException ex, WebRequest request) {
        String correlationId = UUID.randomUUID().toString();
        String requestUrl = getRequestUrl(request);
        
        logger.error("Null pointer exception [correlationId={}, url={}]: {}", correlationId, requestUrl, ex.getMessage(), ex);
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "A required value was missing. Please contact support with the correlation ID."
        );
        
        problemDetail.setTitle("Null Pointer Error");
        problemDetail.setType(URI.create("urn:qodo:command:errors:null-pointer-error"));
        problemDetail.setProperty("correlationId", correlationId);
        problemDetail.setProperty("requestUrl", requestUrl);
        problemDetail.setProperty("timestamp", Instant.now().toString());
        
        return problemDetail;
    }
    
    /**
     * Handles HttpRequestMethodNotSupportedException when an unsupported HTTP method is used.
     * This prevents noisy error logs for requests using wrong HTTP methods (e.g., GET on POST-only endpoints).
     *
     * @param ex      the thrown HttpRequestMethodNotSupportedException
     * @param request the current web request
     * @return a problem detail indicating that the HTTP method is not allowed
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupportedException(HttpRequestMethodNotSupportedException ex, WebRequest request) {
        String requestUrl = getRequestUrl(request);
        
        // Log at debug level since these are often from bots/scanners or client errors
        logger.debug("Unsupported HTTP method [url={}, method={}, supportedMethods={}]", 
            requestUrl,
            ex.getMethod(), 
            ex.getSupportedHttpMethods());
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.METHOD_NOT_ALLOWED,
            String.format("Request method '%s' is not supported for this endpoint.", ex.getMethod())
        );
        
        problemDetail.setTitle("Method Not Allowed");
        problemDetail.setType(URI.create("urn:qodo:command:errors:method-not-allowed"));
        problemDetail.setProperty("method", ex.getMethod());
        problemDetail.setProperty("supportedMethods", ex.getSupportedHttpMethods());
        problemDetail.setProperty("requestUrl", requestUrl);
        problemDetail.setProperty("timestamp", Instant.now().toString());
        
        return problemDetail;
    }
    
    /**
     * Extracts the full request URL from the WebRequest.
     * Returns the complete URL including query parameters if present.
     * Returns "unknown" if the URL cannot be determined.
     */
    private String getRequestUrl(WebRequest request) {
        try {
            if (request instanceof ServletWebRequest servletWebRequest) {
                HttpServletRequest httpRequest = servletWebRequest.getRequest();
                String queryString = httpRequest.getQueryString();
                String requestUrl = httpRequest.getRequestURL().toString();
                
                if (queryString != null && !queryString.isEmpty()) {
                    return requestUrl + "?" + queryString;
                }
                return requestUrl;
            }
            
            String description = request.getDescription(false);
            // Handle edge cases where description might be invalid (e.g., "//")
            if (description.isEmpty() || description.equals("//")) {
                return "unknown";
            }
            return description;
        } catch (Exception e) {
            logger.debug("Failed to extract request URL: {}", e.getMessage());
            return "unknown";
        }
    }

    /**
     * Handles NoResourceFoundException for static resources.
     * These are typically scanning/phishing attempts looking for vulnerable endpoints.
     * Logs at DEBUG level to avoid log pollution from automated scanners.
     *
     * @param ex      the thrown NoResourceFoundException
     * @param request the current web request
     * @return a problem detail indicating the resource was not found
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFoundException(NoResourceFoundException ex, WebRequest request) {
        String requestUrl = getRequestUrl(request);
        String resourcePath = ex.getResourcePath();

        // Extract IP address for security logging
        String clientIp = "unknown";
        if (request instanceof ServletWebRequest servletWebRequest) {
            HttpServletRequest httpRequest = servletWebRequest.getRequest();
            clientIp = httpRequest.getRemoteAddr();
        }

        // Log at debug level with security context
        logger.debug("Potential security scan - static resource not found [ip={}, path={}, url={}]",
                     clientIp, resourcePath, requestUrl);

        // Only log stack trace if trace level is enabled
        if (logger.isTraceEnabled()) {
            logger.trace("NoResourceFoundException details:", ex);
        }

        // Return minimal information to avoid revealing internal structure
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Resource not found"
        );

        problemDetail.setTitle("Not Found");
        problemDetail.setType(URI.create("urn:qodo:command:errors:resource-not-found"));
        problemDetail.setProperty("timestamp", Instant.now().toString());

        // Don't include sensitive information like paths or correlation IDs
        // to avoid helping attackers map the application

        return problemDetail;
    }
}
