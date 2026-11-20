package archfixtures.jsontree.usage;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonNodeField {

    private ObjectNode payload;

    public void clear() {
        payload = null;
    }
}
