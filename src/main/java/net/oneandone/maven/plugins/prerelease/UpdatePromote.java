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

import net.oneandone.maven.plugins.prerelease.core.Archive;
import net.oneandone.maven.plugins.prerelease.core.Prerelease;
import net.oneandone.maven.plugins.prerelease.core.WorkingCopy;
import net.oneandone.maven.plugins.prerelease.util.Maven;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Updates and promotes a prerelease. Convenience goal to build a release in one call.
 */
@Mojo(name = "update-promote")
public class UpdatePromote extends Promote {
    public void doExecute(Archive archive) throws Exception {
        Prerelease prerelease;

        prerelease = doCreate(archive, true);
        prerelease.promote(getLog(), propertyArgs(), createTagMessage, revertTagMessage, nextIterationMessage, maven(),
                allowSnapshots, allowPrereleaseSnapshots);
        workingCopy().update(getLog());
    }
}
