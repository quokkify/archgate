package archfixtures.jsontree.usage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class JsonNodeSignatures {

    public String describe(JsonNode node) {
        return String.valueOf(node);
    }

    public ArrayNode empty() {
        return null;
    }
}
