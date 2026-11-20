package archfixtures.jsontree.usage;

import archfixtures.jsontree.wrapper.JsonBody;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;

public class ValidWrapperUsage {

    private final JsonBody body = new JsonBody().setField("item", "order");

    public String readItem() {
        JsonBody parsed = new JsonBody("{\"item\":\"order\"}")
                .setAt("/payment/amount", 10)
                .removeAt("/legacy");
        JsonNode item = parsed.requiredAt("/item");
        return item.asText() + parsed.at("/missing").isMissingNode() + parsed.readAt("/payment/amount", Integer.class);
    }

    public int readTree(JsonBody json) {
        int total = json.requiredAt("/payment").path("amount").asInt()
                + json.requiredAt("/items").size();
        for (JsonNode item : json.requiredAt("/items")) {
            total += item.get("value").asInt();
        }
        Optional<ObjectNode> found = json.findFirstObjectByFieldValue("type", "express");
        return total
                + found.map(node -> node.path("value").asInt()).orElse(0)
                + json.json().path("count").asInt();
    }

    public String readAsText(JsonBody json) {
        return Optional.of(json.requiredAt("/item")).map(JsonNode::asText).orElseThrow();
    }

    public TypeReference<String> unrelatedTypeReference() {
        return new TypeReference<>() {};
    }

    public JsonBody body() {
        return body;
    }
}
