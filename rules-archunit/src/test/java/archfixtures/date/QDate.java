package archfixtures.date;

import com.querydsl.core.types.dsl.BeanPath;
import java.util.Date;

@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public class QDate extends BeanPath<Object> {

    private Date created;

    public QDate() {
        super(Object.class, "date");
    }
}
