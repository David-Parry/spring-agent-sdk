/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.davidparry.agent.core.api;

import com.davidparry.agent.core.pojo.CommandSession;

import java.util.List;

/**
 * Contract for processing a CommandSession and its TaskResponses and executing the appropriate
 * next-step logic (e.g., publishing messages, coordinating services).
 */
public interface Handler {
   /**
    * Suffix used for naming handler beans/keys.
    */
   String HANDLER_SUFFIX = "-handler";
   /**
    * Handle the command session and task responses.
    *
    * @param commandSession   the current command session context
    * @param allTaskResponses list of task responses from the agent execution
    */
   void handle(CommandSession commandSession, List<TaskResponse> allTaskResponses);
}
