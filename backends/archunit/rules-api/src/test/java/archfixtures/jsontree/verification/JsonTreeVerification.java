package archfixtures.jsontree.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import java.util.stream.StreamSupport;

public class JsonTreeVerification extends BaseVerification<JsonTreeVerification> {

    public JsonTreeVerification verifyItemIsInvalid(String body, String kind) {
        JsonNode item = findItem(body, kind);
        if (!item.path("valid").equals(BooleanNode.FALSE)) {
            throw new AssertionError("item is valid");
        }
        return self();
    }

    private static JsonNode findItem(String body, String kind) {
        JsonNode responseBody;
        try {
            responseBody = new ObjectMapper().readValue(body, JsonNode.class);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        return StreamSupport.stream(responseBody.path("items").spliterator(), false)
                .filter(item -> kind.equals(item.path("kind").asText()))
                .findFirst()
                .orElseThrow();
    }
}
