package com.davidparry.agent.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.davidparry.agent.core.api.Handler.HANDLER_SUFFIX;

@Service(EndFlowCleanup.TYPE + HANDLER_SUFFIX)
public class EndNodeHandler extends BaseHandler {
    public static final String END_FLOW_CLEANUP = "end_flow_cleanup";
    private static final String MSG = "No Next Node Found or Service Handler for prior Agent";

    public EndNodeHandler(MessagePublisher messagePublisher, ObjectMapper objectMapper) {
        super(messagePublisher, objectMapper);
    }

    @Override
    public String type() {
        return EndFlowCleanup.TYPE;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> map) {
        map.put(END_FLOW_CLEANUP, MSG);
        return map;
    }


}
