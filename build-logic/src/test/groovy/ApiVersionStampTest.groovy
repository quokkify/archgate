import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

class ApiVersionStampTest {

    @Test
    void releaseVersionIsStampedAsIs() {
        assertEquals('0.1.0', ArchgateVersions.apiVersionStamp('0.1.0'))
    }

    @Test
    void snapshotVersionIsStampedAsDev() {
        assertEquals('dev-SNAPSHOT', ArchgateVersions.apiVersionStamp('0.1.0-SNAPSHOT'))
    }

    @Test
    void zeroSnapshotVersionIsStampedAsDev() {
        assertEquals('dev-SNAPSHOT', ArchgateVersions.apiVersionStamp('0.0.0-SNAPSHOT'))
    }
}
