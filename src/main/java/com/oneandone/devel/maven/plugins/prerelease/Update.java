/* Copyright (c) 1&1. All Rights Reserved. */

package com.oneandone.devel.maven.plugins.prerelease;

import com.oneandone.devel.maven.plugins.prerelease.core.Archive;
import com.oneandone.devel.maven.plugins.prerelease.core.Descriptor;
import com.oneandone.devel.maven.plugins.prerelease.core.Prerelease;
import com.oneandone.devel.maven.plugins.prerelease.core.WorkingCopy;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Checks if there is a pre-release for the last change in your svn working directory, creates one if not.
 */
@Mojo(name = "update")
public class Update extends ProjectBase {
    @Override
    public void doExecute(Archive archive) throws Exception {
        WorkingCopy workingCopy;
        Descriptor descriptor;
        long revision;
        Prerelease prerelease;

        workingCopy = checkedWorkingCopy();
        getLog().info("checking project ...");
        revision = workingCopy.revision();
        descriptor = Descriptor.create(project, revision, schedule());
        workingCopy.checkCompatibility(descriptor);
        setTarget(archive.target(revision));
        if (target.exists()) {
            getLog().info("prerelease already exists: " + descriptor.getName());
        } else {
            prerelease = Prerelease.create(getLog(), descriptor, target, alwaysUpdate, false, session.getUserProperties());
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
