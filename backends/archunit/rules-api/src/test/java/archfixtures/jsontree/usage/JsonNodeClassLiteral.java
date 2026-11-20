package archfixtures.jsontree.usage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonNodeClassLiteral {

    public String parse(String json) throws Exception {
        return new ObjectMapper().readValue(json, ObjectNode.class).toString();
    }
}
