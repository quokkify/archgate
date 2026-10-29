import org.testng.annotations.Test

import static org.assertj.core.api.Assertions.assertThat

class ApiVersionStampTest {

    @Test
    void releaseVersionIsStampedAsIs() {
        assertThat(ArchgateVersions.apiVersionStamp('0.1.0')).isEqualTo('0.1.0')
    }

    @Test
    void snapshotVersionIsStampedAsDev() {
        assertThat(ArchgateVersions.apiVersionStamp('0.1.0-SNAPSHOT')).isEqualTo('dev-SNAPSHOT')
    }

    @Test
    void zeroSnapshotVersionIsStampedAsDev() {
        assertThat(ArchgateVersions.apiVersionStamp('0.0.0-SNAPSHOT')).isEqualTo('dev-SNAPSHOT')
    }
}
