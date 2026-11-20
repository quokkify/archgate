package archfixtures.jsontree.usage;

import archfixtures.jsontree.wrapper.JsonBody;

public class NodeInstanceFactories {

    public int freshObject(JsonBody body) {
        var fresh = body.json().objectNode();
        return fresh.size();
    }

    public int freshArray(JsonBody body) {
        var fresh = body.json().arrayNode();
        return fresh.size();
    }

    public int freshText(JsonBody body) {
        var fresh = body.json().textNode("order");
        return fresh.asText().length();
    }

    public int copy(JsonBody body) {
        var copy = body.json().deepCopy();
        return copy.size();
    }
}
