/* Copyright (c) 1&1. All Rights Reserved. */

package com.oneandone.devel.maven.plugins.prerelease;

import com.oneandone.devel.maven.plugins.prerelease.core.Archive;
import com.oneandone.devel.maven.plugins.prerelease.core.Prerelease;
import com.oneandone.devel.maven.plugins.prerelease.core.WorkingCopy;
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
        prerelease.build(getLog(), session.getUserProperties(), arguments);
    }
}
