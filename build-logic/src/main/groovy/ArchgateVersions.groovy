class ArchgateVersions {

    static final String DEV_STAMP = 'dev-SNAPSHOT'

    static String apiVersionStamp(String version) {
        version.endsWith('-SNAPSHOT') ? DEV_STAMP : version
    }
}
