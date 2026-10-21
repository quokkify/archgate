package archfixtures.logger;

import com.querydsl.core.types.dsl.BeanPath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public class QLogger extends BeanPath<Object> {

    private static final Logger log = LogManager.getLogger(QLogger.class);

    public QLogger() {
        super(Object.class, "logger");
    }
}
