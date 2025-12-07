/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.command.internal.service;

import ai.qodo.command.internal.pojo.CommandSession;

/**
 * Contract for services that process messages during a command flow.
 * Implementations typically initialize with a CommandSession and then execute
 * their processing logic.
 */
public interface MessageService {
    /**
     * Suffix used for naming service beans/keys.
     */
    String SERVICE_SUFFIX = "-service";
    /**
     * Type identifier used when processing cannot determine a successful next step.
     */
    String INCOMPLETE_NODE = "incomplete";

    /**
     * Executes the service's processing logic.
     */
    void process();

    /**
     * Initializes the service with the given command session context.
     *
     * @param commandSession the current command session context
     */
    void init(CommandSession commandSession);

    /**
     * Returns the unique key identifying this service, typically used for routing.
     *
     * @return non-null service key
     */
    String serviceKey();
}
