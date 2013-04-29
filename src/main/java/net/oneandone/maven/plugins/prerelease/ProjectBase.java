/* Copyright (c) 1&1. All Rights Reserved. */

package net.oneandone.maven.plugins.prerelease;

import net.oneandone.maven.plugins.prerelease.core.Archive;
import net.oneandone.maven.plugins.prerelease.core.Descriptor;
import net.oneandone.maven.plugins.prerelease.core.Schedule;
import net.oneandone.maven.plugins.prerelease.core.Target;
import net.oneandone.maven.plugins.prerelease.core.WorkingCopy;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

public abstract class ProjectBase extends Base {
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    @Parameter
    private List<Plugin> plugins = new ArrayList<>();


    /**
     * Specifies where to create a symlink to the prerelease checkout. No symlink is created if the prerelease has no checkout (and thus is
     * broken). No symlink is created if not specified.
     */
    @Parameter(property = "prerelease.checkoutLink", defaultValue = "${basedir}/target/checkout")
    private String checkoutLink;

    protected Target target = null;

    public ProjectBase() {
    }

    @Override
    public void doExecute() throws Exception {
        FileNode directory;

        directory = Archive.directory(world.file(archiveRoot), project);
        try (Archive archive = Archive.open(directory, lockTimeout, getLog())) {
            try {
                doExecute(archive);
            } finally {
                if (target != null) {
                    target.checkoutLinkOpt(checkoutLink);
                }
            }
            if (definesTarget()) {
                // check this only if the command completed successfully
                if (target == null) {
                    throw new IllegalStateException("missing target");
                }
            }
        }
    }

    public abstract void doExecute(Archive archive) throws Exception;

    public void setTarget(Target t) {
        this.target = t;
    }

    protected boolean definesTarget() {
        return true;
    }


    public FileNode basedir() {
        return world.file(project.getBasedir());
    }

    protected WorkingCopy checkedWorkingCopy() throws Exception {
        WorkingCopy workingCopy;

        getLog().info("checking working copy ...");
        workingCopy = WorkingCopy.load(basedir());
        getLog().debug("revisions: " + workingCopy.revisions);
        getLog().debug("changes: " + workingCopy.changes);
        workingCopy.check();
        return workingCopy;
    }

    protected Descriptor checkedDescriptor(WorkingCopy workingCopy) throws Exception {
        Descriptor descriptor;
        long revision;

        getLog().info("checking project ...");
        revision = workingCopy.revision();
        descriptor = Descriptor.checkedCreate(world, project, revision, schedule());
        workingCopy.checkCompatibility(descriptor);
        return descriptor;
    }

    protected Schedule schedule() {
        return new Schedule(plugins);
    }
}
