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
import net.oneandone.maven.plugins.prerelease.core.Project;
import net.oneandone.maven.plugins.prerelease.core.Storages;
import net.oneandone.maven.plugins.prerelease.core.Target;
import net.oneandone.maven.plugins.prerelease.core.WorkingCopy;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

public abstract class ProjectBase extends Base {
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /** Set to true to deploy snapshots for every successfully built prerelease */
    @Parameter(property = "prerelease.snapshots", defaultValue = "false")
    protected boolean snapshots;

    /** Report errors for snapshot dependencies, plugins or parents when false. True is useful for testing. */
    @Parameter(property = "prerelease.allowSnapshots", defaultValue = "false")
    protected boolean allowSnapshots;

    /** Report errors for snapshot dependencies to the prerelease plugin. True is used in integration tests. */
    @Parameter(property = "prerelease.allowPrereleaseSnapshots", defaultValue = "false")
    protected boolean allowPrereleaseSnapshots;

    /**
     * Specifies where to create a symlink to the prerelease checkout. No symlink is created if the prerelease has no checkout yet
     * (and thus is broken). No symlink is created if not specified.
     */
    @Parameter(property = "prerelease.checkoutLink", defaultValue = "${basedir}/target/checkout")
    private String checkoutLink;

    protected Target target = null;

    public ProjectBase() {
    }

    @Override
    public void doExecute() throws Exception {
        Storages storages;

        storages = storages();
        try (Archive archive = storages.open(Project.forMavenProject(project), lockTimeout, getLog())) {
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
        Log log;
        WorkingCopy workingCopy;

        log = getLog();
        log.info("checking working copy ...");
        workingCopy = WorkingCopy.load(basedir());
        if (log.isDebugEnabled()) {
            log.debug("revisions: " + workingCopy.revisions);
            log.debug("changes: " + workingCopy.changes);
        }
        workingCopy.check();
        return workingCopy;
    }

    protected Descriptor descriptorForWorkingcopy(WorkingCopy workingCopy) throws Exception {
        Descriptor descriptor;
        long revision;

        getLog().info("checking project ...");
        revision = workingCopy.revision();
        descriptor = Descriptor.create(version(), project, revision, storages());
        workingCopy.checkCompatibility(descriptor);
        return descriptor;
    }
}
