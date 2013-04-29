/* Copyright (c) 1&1. All Rights Reserved. */

package com.oneandone.devel.maven.plugins.prerelease;

import com.oneandone.devel.maven.Maven;
import com.oneandone.devel.maven.plugins.prerelease.core.Descriptor;
import com.oneandone.devel.maven.plugins.prerelease.core.Prerelease;
import com.oneandone.devel.maven.plugins.prerelease.core.Target;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

import java.lang.reflect.Field;

/**
 * Preform build without a working copy.
 *
 */
@Mojo(name = "bare-build", requiresProject = false)
public class BareBuild extends BareBase {
    @Override
    public void doExecute(Maven maven, MavenProject project, Target target, Descriptor descriptor) throws Exception {
        Prerelease prerelease;

        prerelease = target.loadOpt();
        if (prerelease == null) {
            throw new MojoExecutionException("prerelease not found: " + descriptor.revision);
        }
        prerelease.build(getLog(), session.getUserProperties(), arguments(project));
    }

    private String[] arguments(MavenProject project) throws Exception {
        org.apache.maven.plugin.Mojo mojo;
        Field field;

        mojo = mojo(project, "prerelease:build");
        field = mojo.getClass().getDeclaredField("arguments");
        field.setAccessible(true);
        return (String[]) field.get(mojo);
    }

}
