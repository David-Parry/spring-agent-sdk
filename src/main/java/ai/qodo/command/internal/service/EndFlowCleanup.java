/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.service;

import ai.qodo.command.internal.pojo.CommandSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.stereotype.Service;

import static ai.qodo.command.internal.service.EndFlowCleanup.TYPE;

/**
 * Default usage for an end node if one is not given in the flow for the agent with an agent -service
 */
@Service(TYPE + MessageService.SERVICE_SUFFIX)
public class EndFlowCleanup implements MessageService, BeanNameAware {
    public static final String TYPE = "end_node";
    private static final Logger logger = LoggerFactory.getLogger(EndFlowCleanup.class);
    private CommandSession commandSession;
    private String serviceKey;

    @Override
    public void process() {
        logger.debug("processing : {}", commandSession);
    }

    @Override
    public void init(CommandSession commandSession) {
        this.commandSession = commandSession;
    }

    @Override
    public String serviceKey() {
        return serviceKey;
    }

    @Override
    public void setBeanName(String name) {
        this.serviceKey = name;
    }
}
