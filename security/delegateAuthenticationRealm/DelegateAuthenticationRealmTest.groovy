import spock.lang.Specification
import org.artifactory.security.User

import static org.jfrog.artifactory.client.ArtifactoryClient.create
import org.jfrog.artifactory.client.model.builder.UserBuilder

class DelegateAuthenticationRealmTest extends Specification {
    def 'delegate authentication realm test'() {
        setup:
        def baseurl1 = 'http://localhost:8081/artifactory'
        def baseurl2 = 'http://localhost:8088/artifactory'
        def artifactory1 = create(baseurl1, 'admin', 'password')
        def artifactory2 = create(baseurl2, 'admin', 'password')

        when:
        UserBuilder userBuilder = artifactory1.security().builders().userBuilder()
        def user1 = userBuilder.name("newuser")
        .email("newuser@jfrog.com")
        .admin(true)
        .profileUpdatable(true)
        .password("password")
        .build();
        artifactory1.security().createOrUpdate(user1)

        def auth = "Basic ${'newuser:password'.bytes.encodeBase64()}"
        def conn = new URL("${baseurl2}/api/trash/empty").openConnection()
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', auth)
        assert conn.responseCode == 202
        conn.disconnect()

        then:
        def user2 = artifactory2.security().user("newuser")
        String name = user2.getName()
        String email = user2.getEmail()
        boolean profileUpdatable = user2.isProfileUpdatable()
        boolean isAdmin = user2.isAdmin()

        name == "newuser"
        email == "newuser@jfrog.com"
        isAdmin == true
        profileUpdatable == true

        cleanup:
        String result1 = artifactory1.security().deleteUser("newuser")
        String result2 = artifactory2.security().deleteUser("newuser")
    }
}
