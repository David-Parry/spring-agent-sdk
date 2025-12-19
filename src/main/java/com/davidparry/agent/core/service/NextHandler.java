package com.davidparry.agent.core.service;

import com.davidparry.agent.core.api.Handler;
import com.davidparry.agent.core.api.TaskResponse;
import com.davidparry.agent.core.pojo.CommandSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.davidparry.agent.core.api.Handler.HANDLER_SUFFIX;
import static com.davidparry.agent.core.service.NextHandler.TYPE;

@Service(TYPE + HANDLER_SUFFIX)
@Scope("prototype")
public class NextHandler extends BaseHandler {
    public static final String TYPE = "next_node";
    // setting the nextAgent needs to be prototype
    public String nextAgent;
    public NextHandler(MessagePublisher messagePublisher, ObjectMapper objectMapper) {
        super(messagePublisher, objectMapper);
    }
    @Override
    public String type() {
        return nextAgent;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> map) {
        // pass it back this is for consumers of the sdk to programmatically get invoked
        return map;
    }

    @Override
    public void handle(CommandSession commandSession, List<TaskResponse> allTaskResponses) {
        nextAgent = commandSession.agentCommand().next();
        super.handle(commandSession,allTaskResponses);
    }


}
