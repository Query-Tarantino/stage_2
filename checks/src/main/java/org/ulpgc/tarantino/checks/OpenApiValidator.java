package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ulpgc.tarantino.common.json.TarantinoJson;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class OpenApiValidator {

    private static final ObjectMapper MAPPER = TarantinoJson.mapper();

    private final Map<String, Object> openApi;

    public OpenApiValidator(Map<String, Object> openApi) {
        this.openApi = openApi;
    }

    public static OpenApiValidator load(Path path) throws IOException {
        try (InputStream stream = Files.newInputStream(path)) {
            Map<String, Object> parsed = new Yaml().load(stream);
            return new OpenApiValidator(parsed);
        }
    }

    public void validateResponse(
            String method,
            String requestPath,
            int statusCode,
            Map<String, List<String>> headers,
            String responseBody) {
        String template = matchPathTemplate(requestPath);
        Map<String, Object> operation = operation(template, method.toLowerCase());
        Map<String, Object> responseDef = responseDefinition(operation, statusCode, method, template);
        validateHeaders(responseDef, headers, method, template, statusCode);
        validateBody(responseDef, headers, responseBody, method, template, statusCode);
    }

    private String matchPathTemplate(String path) {
        String cleanPath = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        Map<String, Object> paths = section(openApi, "paths");
        if (paths.containsKey(cleanPath)) {
            return cleanPath;
        }
        for (String template : paths.keySet()) {
            if (matchesTemplate(template, cleanPath)) {
                return template;
            }
        }
        throw new AssertionError("Path not declared in OpenAPI: " + cleanPath);
    }

    private static boolean matchesTemplate(String template, String path) {
        String regex = template.replaceAll("\\{[^/]+\\}", "[^/]+");
        return Pattern.matches("^" + regex + "$", path);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> operation(String template, String method) {
        Map<String, Object> pathItem = (Map<String, Object>) section(openApi, "paths").get(template);
        if (!pathItem.containsKey(method)) {
            throw new AssertionError("Method " + method.toUpperCase() + " not allowed for path " + template);
        }
        return (Map<String, Object>) pathItem.get(method);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> responseDefinition(
            Map<String, Object> operation, int statusCode, String method, String template) {
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
        String code = String.valueOf(statusCode);
        if (!responses.containsKey(code)) {
            throw new AssertionError("Status code " + statusCode + " not declared for " + method + " " + template);
        }
        return resolveRef((Map<String, Object>) responses.get(code));
    }

    @SuppressWarnings("unchecked")
    private void validateHeaders(
            Map<String, Object> responseDef,
            Map<String, List<String>> headers,
            String method,
            String template,
            int statusCode) {
        if (!responseDef.containsKey("headers")) {
            return;
        }
        Map<String, Object> declaredHeaders = (Map<String, Object>) responseDef.get("headers");
        for (Map.Entry<String, Object> headerEntry : declaredHeaders.entrySet()) {
            String headerName = headerEntry.getKey();
            Map<String, Object> headerDef = resolveRef((Map<String, Object>) headerEntry.getValue());
            List<String> values = findHeader(headers, headerName);
            if (values == null || values.isEmpty()) {
                throw new AssertionError("Missing required header " + headerName + " on " + method + " " + template);
            }
            Map<String, Object> schema = resolveRef((Map<String, Object>) headerDef.get("schema"));
            validateHeaderValue(headerName, values.getFirst(), schema);
        }
    }

    private void validateHeaderValue(String headerName, String value, Map<String, Object> schema) {
        if (schema.containsKey("const")) {
            String expected = String.valueOf(schema.get("const"));
            if (!expected.equals(value)) {
                throw new AssertionError("Header " + headerName + " expected " + expected + " but was " + value);
            }
        }
        if (schema.containsKey("pattern")) {
            String pattern = String.valueOf(schema.get("pattern"));
            if (!Pattern.matches(pattern, value)) {
                throw new AssertionError("Header " + headerName + " does not match pattern " + pattern + ": " + value);
            }
        }
    }

    private static List<String> findHeader(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> headerEntry : headers.entrySet()) {
            if (headerEntry.getKey().equalsIgnoreCase(name)) {
                return headerEntry.getValue();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void validateBody(
            Map<String, Object> responseDef,
            Map<String, List<String>> headers,
            String responseBody,
            String method,
            String template,
            int statusCode) {
        if (!responseDef.containsKey("content")) {
            return;
        }
        Map<String, Object> content = (Map<String, Object>) responseDef.get("content");
        List<String> contentTypes = findHeader(headers, "Content-Type");
        String actualType = (contentTypes == null || contentTypes.isEmpty()) ? "" : contentTypes.getFirst();
        String matchingKey = content.keySet().stream()
                .filter(expected -> actualType.toLowerCase().startsWith(expected.toLowerCase()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Content-Type " + actualType + " does not match expected for " + method + " " + template));
        Map<String, Object> mediaType = (Map<String, Object>) content.get(matchingKey);
        if (!mediaType.containsKey("schema")) {
            return;
        }
        Map<String, Object> schema = resolveRef((Map<String, Object>) mediaType.get("schema"));
        try {
            JsonNode json = MAPPER.readTree(responseBody);
            validateJsonAgainstSchema(json, schema, "");
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Response body is not valid JSON: " + responseBody, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateJsonAgainstSchema(JsonNode node, Map<String, Object> schema, String path) {
        schema = resolveRef(schema);
        if (schema.containsKey("allOf")) {
            Map<String, Object> merged = new HashMap<>();
            Map<String, Object> mergedProperties = new HashMap<>();
            List<String> mergedRequired = new ArrayList<>();
            boolean additionalPropertiesFalse = false;
            for (Object subObject : (List<Object>) schema.get("allOf")) {
                Map<String, Object> subSchema = resolveRef((Map<String, Object>) subObject);
                if (subSchema.containsKey("properties")) {
                    mergedProperties.putAll((Map<String, Object>) subSchema.get("properties"));
                }
                if (subSchema.containsKey("required")) {
                    mergedRequired.addAll((List<String>) subSchema.get("required"));
                }
                if (Boolean.FALSE.equals(subSchema.get("additionalProperties"))) {
                    additionalPropertiesFalse = true;
                }
            }
            merged.put("type", "object");
            merged.put("properties", mergedProperties);
            if (!mergedRequired.isEmpty()) {
                merged.put("required", mergedRequired);
            }
            if (additionalPropertiesFalse || Boolean.FALSE.equals(schema.get("additionalProperties"))) {
                merged.put("additionalProperties", false);
            }
            validateTypeAndConstraints(node, merged, path);
            return;
        }
        if (schema.containsKey("oneOf")) {
            boolean matched = false;
            for (Object subObject : (List<Object>) schema.get("oneOf")) {
                try {
                    validateJsonAgainstSchema(node, (Map<String, Object>) subObject, path);
                    matched = true;
                    break;
                } catch (AssertionError ignored) {
                }
            }
            if (!matched) {
                throw new AssertionError("Node at " + path + " did not match any oneOf schema: " + node);
            }
            return;
        }
        validateTypeAndConstraints(node, schema, path);
    }

    @SuppressWarnings("unchecked")
    private void validateTypeAndConstraints(JsonNode node, Map<String, Object> schema, String path) {
        Object typeObj = schema.get("type");
        if (typeObj instanceof String type) {
            validateSingleType(node, type, schema, path);
        } else if (typeObj instanceof List<?> types) {
            boolean matched = types.stream().anyMatch(expectedType -> isTypeOf(node, String.valueOf(expectedType)));
            if (!matched) {
                throw new AssertionError("Node at " + path + " expected one of " + types + " but was " + node);
            }
        }
        if (schema.containsKey("enum")) {
            List<String> values = ((List<?>) schema.get("enum")).stream().map(String::valueOf).toList();
            if (!values.contains(node.asText())) {
                throw new AssertionError("Node at " + path + " expected enum in " + values + " but was " + node.asText());
            }
        }
        if (schema.containsKey("minimum") && node.isNumber()) {
            double minimum = ((Number) schema.get("minimum")).doubleValue();
            if (node.asDouble() < minimum) {
                throw new AssertionError("Value " + node.asDouble() + " at " + path + " is less than minimum " + minimum);
            }
        }
        if (schema.containsKey("maximum") && node.isNumber()) {
            double maximum = ((Number) schema.get("maximum")).doubleValue();
            if (node.asDouble() > maximum) {
                throw new AssertionError("Value " + node.asDouble() + " at " + path + " is greater than maximum " + maximum);
            }
        }
        if (schema.containsKey("minItems") && node.isArray()) {
            int minItems = ((Number) schema.get("minItems")).intValue();
            if (node.size() < minItems) {
                throw new AssertionError("Array at " + path + " size " + node.size() + " is less than minItems " + minItems);
            }
        }
        if (schema.containsKey("maxItems") && node.isArray()) {
            int maxItems = ((Number) schema.get("maxItems")).intValue();
            if (node.size() > maxItems) {
                throw new AssertionError("Array at " + path + " size " + node.size() + " is greater than maxItems " + maxItems);
            }
        }
        if (schema.containsKey("pattern") && node.isTextual()) {
            String pattern = String.valueOf(schema.get("pattern"));
            if (!Pattern.compile(pattern).matcher(node.asText()).find()) {
                throw new AssertionError("Value '" + node.asText() + "' at " + path + " does not match pattern " + pattern);
            }
        }
    }

    private static boolean isTypeOf(JsonNode node, String type) {
        return switch (type) {
            case "string" -> node.isTextual();
            case "integer" -> node.isIntegralNumber();
            case "number" -> node.isNumber();
            case "boolean" -> node.isBoolean();
            case "array" -> node.isArray();
            case "object" -> node.isObject();
            case "null" -> node.isNull();
            default -> true;
        };
    }

    @SuppressWarnings("unchecked")
    private void validateSingleType(JsonNode node, String type, Map<String, Object> schema, String path) {
        if (!isTypeOf(node, type)) {
            throw new AssertionError("Node at " + path + " expected type " + type + " but was " + node.getNodeType());
        }
        if ("object".equals(type) && schema.containsKey("required")) {
            List<String> required = (List<String>) schema.get("required");
            for (String req : required) {
                if (!node.has(req)) {
                    throw new AssertionError("Missing required field '" + req + "' at " + path);
                }
            }
        }
        if ("object".equals(type) && Boolean.FALSE.equals(schema.get("additionalProperties"))) {
            Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
            for (Iterator<String> fieldNames = node.fieldNames(); fieldNames.hasNext(); ) {
                String fieldName = fieldNames.next();
                if (!properties.containsKey(fieldName)) {
                    throw new AssertionError("Undeclared field '" + fieldName + "' at " + path + " (additionalProperties: false)");
                }
            }
        }
        if ("object".equals(type) && schema.containsKey("properties")) {
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            for (Iterator<Map.Entry<String, JsonNode>> fields = node.fields(); fields.hasNext(); ) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (properties.containsKey(field.getKey())) {
                    validateJsonAgainstSchema(field.getValue(), (Map<String, Object>) properties.get(field.getKey()), path + "." + field.getKey());
                }
            }
        }
        if ("array".equals(type) && schema.containsKey("items")) {
            Map<String, Object> itemSchema = (Map<String, Object>) schema.get("items");
            for (int index = 0; index < node.size(); index++) {
                validateJsonAgainstSchema(node.get(index), itemSchema, path + "[" + index + "]");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveRef(Map<String, Object> target) {
        if (target != null && target.containsKey("$ref")) {
            String ref = String.valueOf(target.get("$ref"));
            if (ref.startsWith("#/")) {
                String[] parts = ref.substring(2).split("/");
                Map<String, Object> current = openApi;
                for (String part : parts) {
                    current = (Map<String, Object>) current.get(part);
                }
                return current;
            }
        }
        return target;
    }

    private static Map<String, Object> section(Map<String, Object> root, String name) {
        return (Map<String, Object>) root.get(name);
    }
}
