package com.prince.agentic.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.common.response.PageResponse;
import com.prince.agentic.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded, model-safe view of a ToolResult (spec §12). Never emits raw ToolResult or class names. */
@Component
public class ObservationSerializer {

    private final ObjectMapper mapper;
    private final AgentProperties props;

    public ObservationSerializer(ObjectMapper mapper, AgentProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    public AgentObservation toObservation(ToolResult<Object> r) {
        if (!r.success()) {
            return new AgentObservation(r.toolName(), false,
                    r.error() == null ? "tool failed" : r.error().message(),
                    r.error() == null ? null : r.error().code());
        }
        return new AgentObservation(r.toolName(), true, summarize(r.data()), null);
    }

    private String summarize(Object data) {
        Object bounded = boundArrays(data);
        String json;
        try {
            json = mapper.writeValueAsString(bounded);
        } catch (JsonProcessingException e) {
            json = String.valueOf(bounded);
        }
        int max = props.maxObservationChars();
        return json.length() <= max ? json : json.substring(0, max);
    }

    /** Cap top-level and PageResponse content arrays to maxArrayItems. */
    private Object boundArrays(Object data) {
        int cap = props.maxArrayItems();
        if (data instanceof List<?> list) {
            return list.stream().limit(cap).toList();
        }
        if (data instanceof PageResponse<?> pr) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("content", pr.content().stream().limit(cap).toList());
            m.put("page", pr.page());
            m.put("totalElements", pr.totalElements());
            m.put("totalPages", pr.totalPages());
            return m;
        }
        return data;
    }
}
