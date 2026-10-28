package archfixtures.console;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoggingWriter {

    private static final Logger LOG = LogManager.getLogger(LoggingWriter.class);

    public void log(Exception e) {
        LOG.info("compliant");
        LOG.error("compliant", e);
    }

    public void traceToExplicitStream(Exception e) {
        e.printStackTrace(new PrintStream(new ByteArrayOutputStream()));
    }
}
