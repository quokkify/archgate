package archfixtures.jsontree.verification;

import com.fasterxml.jackson.databind.JsonNode;

public class AllowedJsonTreeVerification extends BaseVerification<AllowedJsonTreeVerification> {

    public String describe(JsonNode node) {
        return String.valueOf(node);
    }
}
