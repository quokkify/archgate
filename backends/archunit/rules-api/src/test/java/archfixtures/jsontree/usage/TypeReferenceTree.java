package archfixtures.jsontree.usage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TypeReferenceTree {

    public String parse(String json) throws Exception {
        return new ObjectMapper()
                .readValue(json, new TypeReference<ObjectNode>() {})
                .toString();
    }
}
