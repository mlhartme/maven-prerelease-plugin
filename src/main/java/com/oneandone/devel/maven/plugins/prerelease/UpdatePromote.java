/* Copyright (c) 1&1. All Rights Reserved. */

package com.oneandone.devel.maven.plugins.prerelease;

import com.oneandone.devel.maven.plugins.prerelease.core.Archive;
import com.oneandone.devel.maven.plugins.prerelease.core.Prerelease;
import com.oneandone.devel.maven.plugins.prerelease.core.WorkingCopy;
import org.apache.maven.plugins.annotations.Mojo;

import java.util.Properties;

/**
 * Updates and promotes a pre-release. Convenience goal.
 */
@Mojo(name = "update-promote")
public class UpdatePromote extends Promote {
    public void doExecute(Archive archive) throws Exception {
        WorkingCopy workingCopy;
        Prerelease prerelease;
        boolean existing;

        workingCopy = checkedWorkingCopy();
        setTarget(archive.target(workingCopy.revision()));
        prerelease = target.loadOpt();
        if (prerelease == null) {
            prerelease = Prerelease.create(getLog(), checkedDescriptor(workingCopy), target, alwaysUpdate, true,
                    session.getUserProperties());
            existing = false;
        } else {
            existing = true;
        }
        prerelease.promote(getLog(), problemEmail, maven(), getUser(), existing, session.getUserProperties());
        workingCopy.update(getLog());
    }

    public void saveSchedule(Properties dest) {
        schedule().save(dest, "");
    }
}
