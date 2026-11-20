package archfixtures.jsontree.usage;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public class GenericJsonNodeField {

    private final List<JsonNode> items = List.of();

    public int size() {
        return items.size();
    }
}
