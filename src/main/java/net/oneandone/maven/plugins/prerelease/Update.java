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
import net.oneandone.maven.plugins.prerelease.core.Descriptor;
import net.oneandone.maven.plugins.prerelease.core.Prerelease;
import net.oneandone.maven.plugins.prerelease.core.WorkingCopy;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Checks if there is a prerelease for the last change in your svn working directory, creates one if not.
 */
@Mojo(name = "update")
public class Update extends ProjectBase {
    @Override
    public void doExecute(Archive archive) throws Exception {
        WorkingCopy workingCopy;
        Descriptor descriptor;
        long revision;
        Prerelease prerelease;

        // code differs from Create because the descriptor check is deferred until after Prerelease.create
        workingCopy = checkedWorkingCopy();
        getLog().info("checking project ...");
        revision = workingCopy.revision();
        descriptor = Descriptor.create(project, revision);
        workingCopy.checkCompatibility(descriptor);
        setTarget(archive.target(revision));
        if (target.exists()) {
            getLog().info("prerelease already exists: " + descriptor.getName());
        } else {
            prerelease = Prerelease.create(maven(), propertyArgs(), getLog(), descriptor, target);
            archive.wipe(keep, target.join());
            try {
                descriptor.check(world, project);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                prerelease.target.scheduleRemove(getLog(), "build ok, but prerelease is not promotable: " + e.getMessage());
            }
        }
    }
}
