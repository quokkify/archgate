package archfixtures.jsontree.usage;

import archfixtures.jsontree.wrapper.JsonBody;

public class WrapperTreeMutation {

    public JsonBody putThroughJson() {
        JsonBody body = new JsonBody();
        body.json().put("item", "order");
        return body;
    }

    public void putIntoFoundObject(JsonBody body) {
        body.findFirstObjectByFieldValue("id", "1")
                .ifPresent(node -> node.withArray("tags").add("order"));
    }
}
