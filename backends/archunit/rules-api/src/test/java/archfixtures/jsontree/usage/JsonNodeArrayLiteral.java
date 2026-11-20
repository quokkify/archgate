package archfixtures.jsontree.usage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonNodeArrayLiteral {

    public int count(String json) throws Exception {
        JsonNode[] nodes = new ObjectMapper().readValue(json, JsonNode[].class);
        return nodes.length;
    }

    public int countTexts(String json) throws Exception {
        String[] texts = new ObjectMapper().readValue(json, String[].class);
        return texts.length;
    }
}
