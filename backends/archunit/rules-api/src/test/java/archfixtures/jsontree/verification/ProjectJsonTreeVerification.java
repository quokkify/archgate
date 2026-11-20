package archfixtures.jsontree.verification;

import com.fasterxml.jackson.databind.JsonNode;

public class ProjectJsonTreeVerification extends ProjectBaseVerification {

    @Override
    protected String name() {
        return "project";
    }

    public String describe(JsonNode node) {
        return String.valueOf(node);
    }
}
