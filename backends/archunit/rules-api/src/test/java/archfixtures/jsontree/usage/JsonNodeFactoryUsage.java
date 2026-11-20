package archfixtures.jsontree.usage;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

public class JsonNodeFactoryUsage {

    public String build() {
        return JsonNodeFactory.instance.objectNode().toString() + new TextNode("catalog");
    }
}
