package archfixtures.jsontree.usage;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SuppressWarnings("unchecked")
public class CustomJsonNode extends ObjectNode {

    public CustomJsonNode() {
        super(JsonNodeFactory.instance);
    }
}
