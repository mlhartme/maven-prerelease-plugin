/* Copyright (c) 1&1. All Rights Reserved. */

package net.oneandone.maven.plugins.prerelease;

import com.oneandone.devel.maven.Maven;
import net.oneandone.maven.plugins.prerelease.core.Descriptor;
import net.oneandone.maven.plugins.prerelease.core.Prerelease;
import net.oneandone.maven.plugins.prerelease.core.Target;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

/**
 * Perform update-promote without a working copy. Svn url and revision are passed as arguments, not determined from a working copy.
 */
@Mojo(name = "bare-update-promote", requiresProject = false)
public class BareUpdatePromote extends BareUpdate {
    public void doExecute(Maven maven, MavenProject project, Target target, Descriptor descriptor) throws Exception {
        Prerelease prerelease;
        boolean existing;

        prerelease = target.loadOpt();
        if (prerelease == null) {
            descriptor.check(world, project);
            prerelease = Prerelease.create(getLog(), descriptor, target, alwaysUpdate, true, session.getUserProperties());
            existing = false;
        } else {
            existing = true;
        }
        prerelease.promote(getLog(), problemEmail, maven, getUser(), existing, session.getUserProperties());
    }
}
