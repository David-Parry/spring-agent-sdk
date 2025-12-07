/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple ping controller for basic health status checks.
 * Useful for load balancers, monitoring systems, and quick availability checks.
 */
@RestController
@RequestMapping("/api")
public class PingController {

    private static final Logger logger = LoggerFactory.getLogger(PingController.class);

    /**
     * Basic ping endpoint that returns "pong".
     * This is a lightweight health check that confirms the application is running
     * and able to respond to HTTP requests.
     *
     * @return ResponseEntity with "pong" message
     */
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        logger.debug("Ping endpoint called");
        return ResponseEntity.ok("pong");
    }
}
