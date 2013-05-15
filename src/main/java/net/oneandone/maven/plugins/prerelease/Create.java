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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Creates a prerelease with an uncommitted tag and undeployed artifacts. Creating a prerelease is the first step to create a release;
 * once you have a prerelease, you can quicky get a release by promoting the prerelease.
 *
 * Execute this in the svn working directory of your project. Basically, this goal runs checks, creates an uncommitted tag of your working
 * directory and invokes "mvn clean deploy" in it. Maven is invoked with an alternative deployment repository pointing into a subdirectory
 * of this prerelease. In addition, the "performRelease" property is defined to get the same profiles activation you get during a
 * "release:promote" call,
 *
 * When successful, prereleases are stored in the configured archive directory, previous prerelease are wiped.
 *
 * Checks executed by this goal:
 *
 * 1) no uncommitted changes in your working directory.
 *
 * 2) no pending updates up to the last modified revision
 *
 * 3) no snapshot dependencies or snapshot parent pom.
 */
@Mojo(name = "create")
public class Create extends ProjectBase {
    @Override
    public void doExecute(Archive archive) throws Exception {
        WorkingCopy workingCopy;
        Descriptor descriptor;

        workingCopy = checkedWorkingCopy();
        setTarget(archive.target(workingCopy.revision()));
        if (target.exists()) {
            throw new MojoExecutionException("prerelease already exists: " + workingCopy.revision());
        }
        descriptor = checkedDescriptor(workingCopy);
        Prerelease.create(maven(), getLog(), descriptor, target);
    }
}
