package archfixtures.jsontree.wrapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;

public class JsonBody {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectNode json;

    public JsonBody() {
        this.json = MAPPER.createObjectNode();
    }

    public JsonBody(String text) {
        try {
            this.json = (ObjectNode) MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public JsonBody setField(String name, String value) {
        json.put(name, value);
        return this;
    }

    public JsonBody setAt(String pointer, int value) {
        return this;
    }

    public JsonBody removeAt(String pointer) {
        return this;
    }

    public JsonNode at(String pointer) {
        return json.at(pointer);
    }

    public JsonNode requiredAt(String pointer) {
        return json.required(pointer);
    }

    public <T> T readAt(String pointer, Class<T> type) {
        return MAPPER.convertValue(json.at(pointer), type);
    }

    public Optional<ObjectNode> findFirstObjectByFieldValue(String field, String value) {
        return Optional.empty();
    }

    public ObjectNode json() {
        return json;
    }
}
