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
package net.oneandone.maven.plugins.prerelease.core;

import net.oneandone.maven.plugins.prerelease.util.ChangesXml;
import net.oneandone.maven.plugins.prerelease.util.Maven;
import net.oneandone.maven.plugins.prerelease.util.Subversion;
import net.oneandone.maven.plugins.prerelease.util.Transform;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import net.oneandone.sushi.xml.XmlException;
import org.apache.maven.execution.*;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.lifecycle.internal.*;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Prerelease {
    public static Prerelease load(Target target) throws IOException {
        Descriptor descriptor;
        FileNode workingCopy;

        descriptor = Descriptor.load(target);
        workingCopy = target.join("tags", descriptor.getTagName());
        workingCopy.checkDirectory();
        return new Prerelease(target, workingCopy, descriptor);
    }

    public static Prerelease create(Log log, Descriptor descriptor, Target target, Maven maven, BuilderCommon builderCommon, MojoExecutor mojoExecutor)
            throws Exception {
        MavenSession session;
        Prerelease prerelease;
        FileNode tags;
        FileNode checkout;
        String tagname;
        String tagbase;
        int idx;

        log.info("creating un-committed tag ...");
        if (descriptor.svnTag.endsWith("/")) {
            throw new IllegalArgumentException(descriptor.svnTag);
        }
        idx = descriptor.svnTag.lastIndexOf('/');
        if (idx == -1) {
            throw new IllegalArgumentException(descriptor.svnTag);
        }
        tagbase = descriptor.svnTag.substring(0, idx);
        tagname = descriptor.svnTag.substring(idx + 1);
        target.create();
        try {
            tags = target.join("tags");
            checkout = tags.join(tagname);
            log.debug(target.svnLauncher("checkout", "--depth=empty", tagbase, tags.getAbsolute()).exec());
            log.debug(target.svnLauncher("copy", "-r" + descriptor.revision, descriptor.svnOrig, checkout.getAbsolute()).exec());
            prerelease = new Prerelease(target, checkout, descriptor);
            prerelease.descriptor.save(target);
            Transform.adjustPom(prerelease.checkout.join("pom.xml"), descriptor.previous, descriptor.project.version,
                    descriptor.svnOrig, descriptor.svnTag);
            Archive.adjustChanges(prerelease.checkout, prerelease.descriptor.project.version);
            // no "clean" because we have a vanilla directory from svn
            session = maven.subsession(checkout, "install");
            prerelease.create(log, maven, session);
            log.info("created prerelease in " + prerelease.target);
        } catch (Exception e) {
            target.scheduleRemove(log, "create failed: " + e.getMessage());
            throw e;
        }
        target.removeOthers();
        return prerelease;
    }

    //--

    /** base directory with all data for this prerelease. Subdirectory of the archive directory. */
    public final Target target;
    public final FileNode checkout;
    public final Descriptor descriptor;

    public Prerelease(Target target, FileNode checkout, Descriptor descriptor) throws IOException {
        this.target = target;
        this.checkout = checkout;
        this.descriptor = descriptor;
    }

    public FileNode artifacts() {
        return target.join("artifacts");
    }

    public FileNode frischfleisch() {
        return target.join("frischfleisch.properties");
    }

    public void commit(Log log, String by) throws Failure {
        Launcher launcher;

        log.info("committing tag:");
        launcher = Subversion.launcher(checkout, "commit", "-m",
                "Prerelease " + descriptor.revision + " promoted to " + descriptor.project.version + " by " + by);
        log.info(launcher.toString());
        log.info(launcher.exec());
    }

    public void revertCommit(Log log, String by) throws Failure {
        Launcher launcher;

        launcher = Subversion.launcher(checkout, "delete", "-m reverted promotion of prerelease " + descriptor.revision
                + " promoted by " + by, descriptor.svnTag);
        log.info(launcher.toString());
        log.info(launcher.exec());
    }

    public static Artifact artifact(String origPath) {
        int idx;
        String extension;
        String path;
        String version;
        String name;
        String artifactId;
        String groupId;
        String classifier;

        idx = origPath.lastIndexOf('/');
        if (idx == -1) {
            throw new IllegalArgumentException(origPath);
        }
        path = origPath.substring(0, idx);
        name = origPath.substring(idx + 1);

        idx = path.lastIndexOf('/');
        if (idx == -1) {
            throw new IllegalArgumentException(origPath);
        }
        version = path.substring(idx + 1);
        path = path.substring(0, idx);
        idx = path.lastIndexOf('/');
        if (idx == -1) {
            throw new IllegalArgumentException(origPath);
        }
        artifactId = path.substring(idx + 1);
        groupId = path.substring(0, idx).replace('/', '.');

        idx = name.lastIndexOf('.');
        if (idx == -1) {
            throw new IllegalArgumentException(origPath);
        }
        extension = name.substring(idx + 1);
        name = Strings.removeLeft(name.substring(0, idx), artifactId + "-" + version);
        if (name.isEmpty()) {
            classifier = null;
        } else {
            if (!name.startsWith("-")) {
                throw new IllegalArgumentException(origPath);
            }
            classifier = name.substring(1);
        }
        return new DefaultArtifact(groupId, artifactId, classifier, extension, version);
    }

    public FileNode prepareOrigCommit(Log log) throws IOException, XmlException, SAXException, MojoExecutionException {
        FileNode result;
        ChangesXml changes;

        result = checkout.getWorld().getTemp().createTempDirectory();
        log.debug(target.svnLauncher("co", "--depth", "empty", descriptor.svnOrig, result.getAbsolute()).exec());
        log.debug(Subversion.launcher(result, "up", "pom.xml").exec());

        log.debug(Subversion.launcher(result, "up", "--depth", "empty", "src").exec());
        log.debug(Subversion.launcher(result, "up", "--depth", "empty", "src/changes").exec());
        log.debug(Subversion.launcher(result, "up", "src/changes/changes.xml").exec());
        try {
            changes = ChangesXml.load(result);
        } catch (FileNotFoundException e) {
            log.info("no changes.xml to adjust.");
            changes = null;
        }

        Subversion.launcher(result, "lock", "pom.xml");
        if (changes != null) {
            Subversion.launcher(result, "lock", ChangesXml.PATH);
        }

        // make sure the version we've locked is what we will modify:
        // (or in other words: make sure we see possible changes that were committed between checkout and lock)
        Subversion.launcher(result, "up");

        Transform.adjustPom(result.join("pom.xml"), descriptor.previous, descriptor.next, null, null);
        if (changes != null) {
            changes.releaseDate(descriptor.project.version, new Date());
            changes.save();
        }
        // svn up does not fail for none-existing files!
        return result;
    }

    //--

    public void build(final Log log, boolean alwaysUpdate, Properties userProperties, String ... goals) throws Exception {
        Launcher mvn;

        mvn = Maven.launcher(checkout, userProperties);
        if (alwaysUpdate) {
            mvn.arg("-U");
        }
        mvn.arg(goals);
        log.info(mvn.toString());
        mvn.exec(new LogWriter(log));
    }

    private void create(Log log, Maven maven, MavenSession session) throws Exception {
        MavenProject project;
        FileNode installed;
        Map<String, String> initialProperties;

        project = session.getCurrentProject();
        initialProperties = stringProperties(project);
        try {
            maven.execute(log, session);
        } finally {
            installed = descriptor.project.localRepo(checkout.getWorld());
            if (installed.exists()) {
                installed.move(artifacts());
            }
        }
        saveDeployProperties(project, initialProperties);
        // TODO: check that the workspace is without modifications
    }

    private static Map<String, String> stringProperties(MavenProject project) throws Exception {
        Map<String, String> result;

        result = new HashMap<>();
        for (Map.Entry<Object, Object> entry : project.getProperties().entrySet()) {
            if (entry.getValue() instanceof String) {
                result.put((String) entry.getKey(), (String) entry.getValue());
            } else {
                throw new IllegalStateException(entry.getKey() + ": " + entry.getValue());
            }
        }
        return result;
    }

    private void saveDeployProperties(MavenProject project, Map<String, String> previous) throws Exception {
        String old;
        Map<String, String> deployProperties;

        deployProperties = descriptor.deployProperties;
        for (Map.Entry<Object, Object> entry : project.getProperties().entrySet()) {
            if (entry.getValue() instanceof String) {
                old = previous.get(entry.getKey());
                if (!entry.getValue().equals(old)) {
                    deployProperties.put((String) entry.getKey(), (String) entry.getValue());
                }
            } else {
                throw new IllegalStateException(entry.getKey() + ": " + entry.getValue());
            }
        }
        descriptor.save(target);
    }

    //--

    public static class LogWriter extends Writer {
        private final Log log;
        private final StringBuffer buffer;

        public LogWriter(Log log) {
            this.log = log;
            this.buffer = new StringBuffer();
        }

        @Override
        public void write(int c) throws IOException {
            if (c == '\n') {
                logLine();
            } else {
                buffer.append((char) c);
            }
        }

        @Override
        public void write(char[] array, int ofs, int len) throws IOException {
            for (int i = ofs; i < ofs + len; i++) {
                write(array[i]);
            }
        }

        @Override
        public void flush() {
            // no output, because I'd introduce a line break
        }

        @Override
        public void close() {
            // adds a line break, but that's better than losing the last line if it's not terminated with a line break
            if (buffer.length() > 0) {
                logLine();
            }
        }

        private void logLine() {
            log.info(buffer.toString());
            buffer.setLength(0);
        }
    }

    public void verify(final Log log, String profile, boolean alwaysUpdate, Properties userProperties) throws Exception {
        build(log, alwaysUpdate, userProperties, "verify", /* to save disk space: */ "clean", "-P" + profile);
    }

    //-- promote

    public void promote(Log log, String user, String mandatory,
                        Maven maven, MavenSession session, BuilderCommon builderCommon, MavenProjectHelper projectHelper,
                        MojoExecutor mojoExecutor) throws Exception {
        FileNode origCommit;

        log.info("promoting revision " + descriptor.revision + " to " + descriptor.project);
        origCommit = prepareOrigCommit(log);
        try {
            promoteLocked(log, user, mandatory, origCommit, maven, session, builderCommon, projectHelper, mojoExecutor);
        } catch (Throwable e) { // CAUTION: catching exceptions is not enough -- in particular, out-of-memory during upload is an error!
            try {
                origUnlock(origCommit);
            } catch (Exception nested) {
                e.addSuppressed(nested);
            }
            throw e;
        }
        origUnlock(origCommit);
        log.info("SUCCESS: released " + descriptor.project);
    }

    private void origUnlock(FileNode origCommit) {
        Subversion.launcher(origCommit, "unlock" , "pom.xml");
        if (origCommit.join(ChangesXml.PATH).exists()) {
            Subversion.launcher(origCommit, "unlock" , ChangesXml.PATH);
        }
    }

    /** commit before deploy - because if deployment fails, we can reliably revert the commit. */
    private void promoteLocked(Log log, String user, String mandatory, FileNode origCommit,
                               Maven maven, MavenSession session, BuilderCommon builderCommon, MavenProjectHelper projectHelper,
                               MojoExecutor mojoExecutor) throws Exception {
        MavenProject project;
        MavenProject previous;

        project = maven.loadPom(checkout.join("pom.xml"));
        previous = session.getCurrentProject();
        session.setProjects(Collections.singletonList(project));
        // TODO: why?
        project.setPluginArtifactRepositories(previous.getRemoteArtifactRepositories());
        try {
            commit(log, user);
            try {
                deployPhase(log, mandatory, project, session, builderCommon, projectHelper, mojoExecutor);
            } catch (Exception e) {
                log.info("deployment failed - reverting tag");
                revertCommit(log, user);
                target.scheduleRemove(log, "deployment failed (tag has been reverted): " + e.getMessage());
                throw e;
            }
        } finally {
            session.setProjects(Collections.singletonList(previous));
        }
        try {
            log.info("Update pom and changes ...");
            log.debug(Subversion.launcher(origCommit, "commit", "-m", "Prerelease " + descriptor.revision
                    + " promoted to release " + descriptor.project + " by " + user + ", starting next development iteration.").exec());
            origCommit.deleteTree();
            // Move prerelease directory into REMOVE directory because it's invalid now:
            // tag was committed, and artifacts have been deployed. It's not removed immediately to make
            // distribution file available locally.
            target.scheduleRemove(log, "prerelease has been promoted");
        } catch (Exception e) {
            log.warn("Promote succeeded: your artifacts have been deployed, and your svn tag was created. ");
            log.warn("However, some post-release step failed with this exception:");
            log.warn(e);
            log.warn("Thus, you can use your release, but someone should have a look at this exception.");
        }
    }

    //--

    private void deployPhase(Log log, String mandatory, MavenProject project, MavenSession session, BuilderCommon builderCommon, MavenProjectHelper projectHelper, MojoExecutor mojoExecutor) throws Exception {
        List<MojoExecution> mandatoryExecutions;
        List<MojoExecution> optionalExecutions;
        List<String> mandatories;
        ProjectIndex index;

        mandatories = Separator.COMMA.split(mandatory);
        MavenExecutionPlan executionPlan =
                builderCommon.resolveBuildPlan(session, project, new TaskSegment(false, new LifecycleTask("deploy")), new HashSet<org.apache.maven.artifact.Artifact>());
        mandatoryExecutions = new ArrayList<>();
        optionalExecutions = new ArrayList<>();
        for (MojoExecution obj : executionPlan.getMojoExecutions()) {
            if ("deploy".equals(obj.getLifecyclePhase())) {
                if (mandatories.contains(obj.getPlugin().getArtifactId())) {
                    log.info("mandatory: " + obj.getPlugin().getArtifactId() + ":" + obj.getGoal());
                    mandatoryExecutions.add(obj);
                } else {
                    log.info("optional " + obj.getPlugin().getArtifactId() + ":" + obj.getGoal());
                    optionalExecutions.add(obj);
                }
            }
        }
        index = new ProjectIndex(session.getProjects());
        artifactFiles(project, projectHelper);
        project.getProperties().putAll(descriptor.deployProperties);
        mojoExecutor.execute(session, mandatoryExecutions, index);
        try {
            mojoExecutor.execute(session, optionalExecutions, index);
        } catch (Exception e) {
            log.warn("Promote succeeded: your artifacts have been deployed, and the svn tag was created. ");
            log.warn("However, optional promote goals failed with this exception: ");
            log.warn(e);
            log.warn("Thus, you can use your release, but someone experienced should have a look.");
        }
    }

    private void artifactFiles(MavenProject project, MavenProjectHelper projectHelper) throws IOException {
        FileNode artifacts;
        String name;
        String str;
        String type;
        String classifier;

        artifacts = artifacts();
        for (FileNode file : artifacts.list()) {
            name = file.getName();
            if (name.endsWith(".md5") || name.endsWith(".sha1") || name.endsWith(".asc") || name.equals("_maven.repositories")) {
                // skip
            } else {
                type = file.getExtension();
                if ("pom".equals(type) && !project.getPackaging().equals("pom")) {
                    // ignored
                } else {
                    str = name.substring(0, name.length() - type.length() - 1);
                    str = Strings.removeLeft(str, project.getArtifactId() + "-");
                    str = Strings.removeLeft(str, project.getVersion());
                    if (str.isEmpty()) {
                        project.getArtifact().setFile(file.toPath().toFile());
                    } else {
                        classifier = Strings.removeLeft(str, "-");
                        projectHelper.attachArtifact(project, file.toPath().toFile(), classifier);
                    }
                }
            }
        }
    }
}
