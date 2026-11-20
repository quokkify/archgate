package archfixtures.jsontree.usage;

import com.fasterxml.jackson.databind.JsonNode;

public class AllowedJacksonUsage {

    public String describe(JsonNode node) {
        return String.valueOf(node);
    }
}
