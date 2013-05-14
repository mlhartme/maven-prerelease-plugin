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

import net.oneandone.maven.plugins.prerelease.util.Maven;
import net.oneandone.maven.plugins.prerelease.core.Descriptor;
import net.oneandone.maven.plugins.prerelease.core.Prerelease;
import net.oneandone.maven.plugins.prerelease.core.Target;
import org.apache.maven.lifecycle.internal.BuilderCommon;
import org.apache.maven.lifecycle.internal.MojoExecutor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.lang.reflect.Field;
import java.util.Properties;

/**
 * Perform update-promote without a working copy. Svn url and revision are passed as arguments, not determined from a working copy.
 */
@Mojo(name = "bare-update-promote", requiresProject = false)
public class BareUpdatePromote extends BareUpdate {
    @Component
    protected BuilderCommon builderCommon;

    @Component
    protected MavenProjectHelper projectHelper;

    @Component
    protected MojoExecutor mojoExecutor;

    public void doExecute(Maven maven, MavenProject project, Target target, Descriptor descriptor) throws Exception {
        Prerelease prerelease;

        prerelease = target.loadOpt();
        if (prerelease == null) {
            descriptor.check(world, project);
            prerelease = Prerelease.create(getLog(), descriptor, target, alwaysUpdate, session.getUserProperties());
        }
        prerelease.promote(getLog(), getUser(), getMandatory(project), maven(), session, builderCommon, projectHelper, mojoExecutor);
    }

    // TODO: move into promote goal
    private String getMandatory(MavenProject project) throws Exception {
        org.apache.maven.plugin.Mojo mojo;
        Field field;

        // cannot cast to Promote because it's loaded by a different class loader
        mojo = mojo(project, "net.oneandone.maven.plugins:prerelease:promote");
        field = mojo.getClass().getDeclaredField("mandatory");
        field.setAccessible(true);
        return (String) field.get(mojo);
    }

}
