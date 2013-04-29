/**
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.maven.plugins.prerelease;

/** Extra configuration for a goal that needs extra scheduling when creating/promoting prereleases. */

public class Plugin {
    public static enum Phase {
        /**
         * Normal execution for update-promote and bare-update-promotes, Otherwise, this plugin executes before actually promoting.
         * Example: changes-check.
         */
        BEFORE_PROMOTE,

        /**
         * Only executed after any kind of promoting. Never executes as part of the standard-release build.
         * Example: frischfleisch notification.
         */
        AFTER_PROMOTE
    }

    public final String groupId;
    public final String artifactId;
    public final String goal;
    public final Phase phase;

    /**
     * How to disabled this plugin invokation for none-promoting builds. Key-Value pair. If your plugin does not have a property to skip
     * execution, you can place define a profile to disable it and specifiy the profile-activating property here. */
    public final String skip;

    public Plugin() {   // TODO: for Maven parameter injection
        this(null, null, null, null, null);
    }

    public Plugin(String groupId, String artifactId, String goal, Phase phase, String skip) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.goal = goal;
        this.phase = phase;
        this.skip = skip;
    }

    @Override
    public boolean equals(Object obj) {
        Plugin plugin;

        if (obj instanceof Plugin) {
            plugin = (Plugin) obj;
            return artifactId.equals(plugin.artifactId) && groupId.equals(plugin.groupId)
                    && goal.equals(plugin.goal) && phase.equals(plugin.phase) && phase == plugin.phase;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return artifactId.hashCode();
    }
}
