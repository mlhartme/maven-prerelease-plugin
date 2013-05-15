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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Executes a build on an existing prerelease.
 */
@Mojo(name = "build")
public class Build extends ProjectBase {
    /**
     * Arguments to pass to mvn. Separate with "," when using the property, e.g. "-Dprerelease.build=clean,package,-DskipTests=true"
     */
    @Parameter(property = "prerelease.build", defaultValue = "validate")
    protected String[] arguments;

    @Override
    public void doExecute(Archive archive) throws Exception {
        WorkingCopy workingCopy;
        long revision;
        Prerelease prerelease;

        workingCopy = checkedWorkingCopy();
        revision = workingCopy.revision();
        setTarget(archive.target(revision));
        prerelease = target.loadOpt();
        if (prerelease == null) {
            throw new MojoExecutionException("no prerelease for revision " + revision);
        }
        maven().build(prerelease.checkout, arguments);
    }
}
