package archfixtures.jsontree.usage;

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

public class ValueNodeFactories {

    public String text() {
        return TextNode.valueOf("catalog").toString();
    }

    public String constants() {
        return BooleanNode.TRUE.toString() + NullNode.getInstance();
    }
}
