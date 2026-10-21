package archfixtures.logger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InstanceLogger {

    @SuppressWarnings({"checkstyle:MemberName", "checkstyle:AbbreviationAsWordInName"})
    private final Logger LOG = LogManager.getLogger(InstanceLogger.class);
}
