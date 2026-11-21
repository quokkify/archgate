package archfixtures.date;

import java.util.function.Supplier;

public class ReferencedDate {

    public Object now() {
        Supplier<Object> clock = java.util.Date::new;
        return clock.get();
    }
}
