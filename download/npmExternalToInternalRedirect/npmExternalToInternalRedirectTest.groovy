import spock.lang.Specification

class PluginTest extends Specification {
    def 'not implemented plugin test'() {
        when:
        throw new Exception("Not implemented.")
        then:
        false
    }
}