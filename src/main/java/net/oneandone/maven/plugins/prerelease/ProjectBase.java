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
import net.oneandone.maven.plugins.prerelease.core.Storages;
import net.oneandone.maven.plugins.prerelease.core.Target;
import net.oneandone.maven.plugins.prerelease.core.WorkingCopy;
import net.oneandone.maven.plugins.prerelease.util.Maven;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.plugin.MojoExecutionException;
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

    /** Report errors as warnings only. You can use this to preview "check" result without failing your build. */
    @Parameter(property = "prerelease.ignoreFailure", defaultValue = "false")
    private boolean ignoreFailure;

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
        Archive archive;

        storages = storages();
        archive = storages.open(project, lockTimeout, getLog());
        try {
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
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            if (ignoreFailure) {
                getLog().warn("ignoring plugin failure: " + e.getMessage());
                getLog().debug(e);
            } else {
                throw e;
            }
        } finally {
            storages.close();
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

    private WorkingCopy lazyWorkingCopy;

    protected WorkingCopy workingCopy() throws Exception {
        Log log;

        if (lazyWorkingCopy == null) {
            log = getLog();
            log.info("checking working copy ...");
            lazyWorkingCopy = WorkingCopy.load(basedir());
            if (log.isDebugEnabled()) {
                log.debug("revisions: " + lazyWorkingCopy.revisions);
                log.debug("changes: " + lazyWorkingCopy.changes);
            }
        }
        return lazyWorkingCopy;
    }

    //--

    public Prerelease doCreate(Archive archive, boolean optional) throws Exception {
        Storages storages;
        WorkingCopy workingCopy;
        Descriptor descriptor;
        long revision;
        Prerelease prerelease;
        Maven maven;

        storages = storages();
        workingCopy = workingCopy().check();
        getLog().info("checking project ...");
        revision = workingCopy.revision();
        descriptor = Descriptor.create(version(), project, revision, storages);
        workingCopy.checkCompatibility(descriptor);
        setTarget(archive.target(revision));
        if (target.exists()) {
            if (optional) {
                getLog().info("prerelease already exists: " + descriptor.getName());
            } else {
                throw new MojoExecutionException("prerelease already exists: " + workingCopy.revision());
            }
            prerelease = target.loadOpt(storages());
        } else {
            maven = maven();
            prerelease = Prerelease.create(maven, propertyArgs(), getLog(), descriptor, target);
            archive.wipe(keep);
            if (snapshots) {
                prerelease.deploySnapshot(maven, getLog(), propertyArgs(), project);
            }
        }
        return prerelease;
    }
}
