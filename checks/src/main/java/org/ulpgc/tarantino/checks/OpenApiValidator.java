package org.ulpgc.tarantino.checks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.ulpgc.tarantino.common.json.TarantinoJson;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

public class OpenApiValidator {

    private static final ObjectMapper MAPPER = TarantinoJson.mapper();
    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();
    private static final JsonSchemaFactory SCHEMA_FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private final JsonNode openApi;

    public OpenApiValidator(JsonNode openApi) {
        this.openApi = openApi;
    }

    public static OpenApiValidator load(Path path) throws IOException {
        JsonNode rootNode = YAML_MAPPER.readTree(path.toFile());
        return new OpenApiValidator(rootNode);
    }

    public void validateResponse(
            String method,
            String requestPath,
            int statusCode,
            Map<String, List<String>> headers,
            String responseBody) {
        String template = matchPathTemplate(requestPath);
        JsonNode operationNode = operation(template, method.toLowerCase());
        JsonNode responseDef = responseDefinition(operationNode, statusCode, method, template);
        validateHeaders(responseDef, headers, method, template, statusCode);
        validateBody(responseDef, headers, responseBody, method, template, statusCode);
    }

    public void validateProblem(JsonNode bodyJson) {
        JsonNode problemSchema = openApi.at("/components/schemas/Problem");
        if (problemSchema.isMissingNode()) {
            throw new AssertionError("Problem schema not found in OpenAPI");
        }
        validateWithJsonSchema(bodyJson, problemSchema);
    }

    private String matchPathTemplate(String path) {
        String cleanPath = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        JsonNode pathsNode = openApi.get("paths");
        if (pathsNode == null) {
            throw new AssertionError("No paths found in OpenAPI specification");
        }
        if (pathsNode.has(cleanPath)) {
            return cleanPath;
        }
        Iterable<String> pathNames = pathsNode::fieldNames;
        return StreamSupport.stream(pathNames.spliterator(), false)
                .filter(template -> matchesTemplate(template, cleanPath))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Path not declared in OpenAPI: " + cleanPath));
    }

    private static boolean matchesTemplate(String template, String path) {
        String regex = template.replaceAll("\\{[^/]+\\}", "[^/]+");
        return Pattern.matches("^" + regex + "$", path);
    }

    private JsonNode operation(String template, String method) {
        JsonNode pathItem = openApi.get("paths").get(template);
        if (pathItem == null || !pathItem.has(method)) {
            throw new AssertionError("Method " + method.toUpperCase() + " not allowed for path " + template);
        }
        return pathItem.get(method);
    }

    private JsonNode responseDefinition(
            JsonNode operationNode, int statusCode, String method, String template) {
        JsonNode responsesNode = operationNode.get("responses");
        String codeStr = String.valueOf(statusCode);
        if (responsesNode == null || !responsesNode.has(codeStr)) {
            throw new AssertionError("Status code " + statusCode + " not declared for " + method + " " + template);
        }
        return resolveRef(responsesNode.get(codeStr));
    }

    private void validateHeaders(
            JsonNode responseDef,
            Map<String, List<String>> headers,
            String method,
            String template,
            int statusCode) {
        if (!responseDef.has("headers")) {
            return;
        }
        JsonNode declaredHeaders = responseDef.get("headers");
        Iterable<Map.Entry<String, JsonNode>> headerEntries = declaredHeaders::fields;
        StreamSupport.stream(headerEntries.spliterator(), false)
                .forEach(headerEntry -> {
                    String headerName = headerEntry.getKey();
                    JsonNode headerDef = resolveRef(headerEntry.getValue());
                    List<String> values = findHeader(headers, headerName);
                    if (values == null || values.isEmpty()) {
                        throw new AssertionError("Missing required header " + headerName + " on " + method + " " + template);
                    }
                    if (headerDef.has("schema")) {
                        JsonNode schemaNode = resolveRef(headerDef.get("schema"));
                        validateHeaderValue(headerName, values.getFirst(), schemaNode);
                    }
                });
    }

    private void validateHeaderValue(String headerName, String value, JsonNode schema) {
        if (schema.has("const")) {
            String expected = schema.get("const").asText();
            if (!expected.equals(value)) {
                throw new AssertionError("Header " + headerName + " expected " + expected + " but was " + value);
            }
        }
        if (schema.has("pattern")) {
            String pattern = schema.get("pattern").asText();
            if (!Pattern.matches(pattern, value)) {
                throw new AssertionError("Header " + headerName + " does not match pattern " + pattern + ": " + value);
            }
        }
    }

    private static List<String> findHeader(Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream()
                .filter(headerEntry -> headerEntry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private void validateBody(
            JsonNode responseDef,
            Map<String, List<String>> headers,
            String responseBody,
            String method,
            String template,
            int statusCode) {
        if (!responseDef.has("content")) {
            return;
        }
        JsonNode contentNode = responseDef.get("content");
        List<String> contentTypes = findHeader(headers, "Content-Type");
        String actualType = (contentTypes == null || contentTypes.isEmpty()) ? "" : contentTypes.getFirst();
        Iterable<String> contentKeys = contentNode::fieldNames;
        String matchingContentType = StreamSupport.stream(contentKeys.spliterator(), false)
                .filter(expectedType -> actualType.toLowerCase().startsWith(expectedType.toLowerCase()))
                .findFirst()
                .orElse(null);
        if (matchingContentType == null) {
            throw new AssertionError("Content-Type " + actualType + " does not match expected for " + method + " " + template);
        }
        JsonNode mediaTypeNode = contentNode.get(matchingContentType);
        if (!mediaTypeNode.has("schema")) {
            return;
        }
        JsonNode schemaNode = mediaTypeNode.get("schema");
        try {
            JsonNode bodyJson = MAPPER.readTree(responseBody);
            validateWithJsonSchema(bodyJson, schemaNode);
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Response body is not valid JSON: " + responseBody, exception);
        }
    }

    private void validateWithJsonSchema(JsonNode bodyJson, JsonNode schemaNode) {
        ObjectNode schemaWrapper = MAPPER.createObjectNode();
        schemaWrapper.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        if (openApi.has("components")) {
            schemaWrapper.set("components", openApi.get("components").deepCopy());
        }
        if (schemaNode.has("$ref")) {
            schemaWrapper.set("$ref", schemaNode.get("$ref"));
        } else {
            schemaWrapper.setAll((ObjectNode) schemaNode.deepCopy());
        }
        JsonSchema jsonSchema = SCHEMA_FACTORY.getSchema(schemaWrapper);
        Set<ValidationMessage> validationErrors = jsonSchema.validate(bodyJson);
        if (!validationErrors.isEmpty()) {
            throw new AssertionError("Schema validation failed: " + validationErrors);
        }
    }

    private JsonNode resolveRef(JsonNode node) {
        if (node != null && node.has("$ref")) {
            String ref = node.get("$ref").asText();
            if (ref.startsWith("#/")) {
                String pointer = ref.substring(1);
                return openApi.at(pointer);
            }
        }
        return node;
    }
}
