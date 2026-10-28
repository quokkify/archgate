package archfixtures.logger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StaticNonFinalLogger {

    @SuppressWarnings("checkstyle:MemberName")
    private static Logger LOG = LogManager.getLogger(StaticNonFinalLogger.class);
}
