package org.zmy.observabilityplatform.logging.infrastructure.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.zmy.observabilityplatform.logging.domain.service.SensitiveDataProtector;
import org.zmy.observabilityplatform.logging.domain.service.SensitiveFieldPolicy;

import java.util.Map;

public final class JsonSensitiveDataProtector implements SensitiveDataProtector {
    private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<>() { };
    private static final int MAX_DEPTH = 64;
    private final ObjectMapper objectMapper;

    public JsonSensitiveDataProtector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String protect(String value) {
        return protect(value, 0);
    }

    private String protect(String value, int depth) {
        if (depth >= MAX_DEPTH) {
            return "***";
        }
        try {
            JsonNode node = value == null ? null : objectMapper.readTree(value);
            if (node != null && node.isContainerNode()) {
                return objectMapper.writeValueAsString(protectNode(node, depth + 1));
            }
        } catch (JsonProcessingException ignored) {
            // Text logs and embedded JSON fragments still pass through the text policy.
        }
        return SensitiveFieldPolicy.protectText(value);
    }

    @Override
    public Map<String, Object> protectAttributes(Map<String, Object> attributes) {
        JsonNode node = objectMapper.valueToTree(attributes == null ? Map.of() : attributes);
        return objectMapper.convertValue(protectNode(node, 0), ATTRIBUTES);
    }

    private JsonNode protectNode(JsonNode node, int depth) {
        if (depth >= MAX_DEPTH) {
            return TextNode.valueOf("***");
        }
        if (node.isObject()) {
            var result = objectMapper.createObjectNode();
            node.fields().forEachRemaining(field -> result.set(field.getKey(),
                    SensitiveFieldPolicy.isSensitive(field.getKey()) ? TextNode.valueOf("***")
                            : protectNode(field.getValue(), depth + 1)));
            return result;
        }
        if (node.isArray()) {
            var result = objectMapper.createArrayNode();
            node.forEach(item -> result.add(protectNode(item, depth + 1)));
            return result;
        }
        return node.isTextual() ? TextNode.valueOf(protect(node.textValue(), depth + 1)) : node;
    }
}
