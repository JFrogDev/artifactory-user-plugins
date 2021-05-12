/*
 * Copyright (C) 2014 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.artifactory.security.RealmPolicy
import org.artifactory.security.groups.LdapGroupsSettings

/**
 * An example of attaching Ldap groups to any user (can be internal, SSO etc..)
 *
 * @author Shay Yaakov
 */

realms {
    myrealm([autoCreateUsers: false, realmPolicy: RealmPolicy.ADDITIVE]) {
        authenticate { username, credentials ->
            // Common special or internal users can be skipped
            if (username in ['anonymous', '_internal', 'xray', 'access-admin', 'admin', 'jffe@000', '_system_']
                || username ==~ /^token:jf[a-z]+@[a-z0-9-]+$/) {
                return true
            }
            def settings = new LdapGroupsSettings()
            // 'il-users' is an existing Ldap Group Setting Name in Artifactory
            // All the permissions given to the group will be inherited by the user
            settings.ldapGroupSettingsName = 'il-users'
            def newgroups = security.getCurrentUserGroupNames(settings) as List
            newgroups.removeAll(groups)
            if (!newgroups.isEmpty()) {
                groups += newgroups
            }
            return true
        }

        userExists { username ->
            return true
        }
    }
}
