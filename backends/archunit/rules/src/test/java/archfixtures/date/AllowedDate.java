package archfixtures.date;

import java.time.Instant;
import java.util.Date;

public class AllowedDate {

    public Object fromInstant() {
        return Date.from(Instant.now());
    }
}
