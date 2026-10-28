package archfixtures.date;

import java.time.Instant;
import java.util.Date;

public class MethodDate {

    public long now() {
        return new Date().getTime();
    }

    public Object fromInstant() {
        return Date.from(Instant.now());
    }
}
