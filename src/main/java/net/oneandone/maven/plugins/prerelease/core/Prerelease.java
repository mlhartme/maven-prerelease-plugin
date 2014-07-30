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
import net.oneandone.maven.plugins.prerelease.util.FilteringMojoExecutor;
import net.oneandone.maven.plugins.prerelease.util.Maven;
import net.oneandone.maven.plugins.prerelease.util.PrepareExecutionListener;
import net.oneandone.maven.plugins.prerelease.util.Subversion;
import net.oneandone.maven.plugins.prerelease.util.Transform;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Strings;
import net.oneandone.sushi.util.Substitution;
import net.oneandone.sushi.util.SubstitutionException;
import net.oneandone.sushi.xml.XmlException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Prerelease {
    public static Prerelease load(Target target, List<FileNode> storages) throws IOException {
        Descriptor descriptor;
        FileNode workingCopy;

        descriptor = Descriptor.load(target, storages);
        workingCopy = target.join("tags", descriptor.getTagName());
        workingCopy.checkDirectory();
        return new Prerelease(target, workingCopy, descriptor);
    }

    public static Prerelease create(Maven maven, Map<String, String> propertyArgs, Log log, Descriptor descriptor, Target target) throws Exception {
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
                    descriptor.svnOrig, descriptor.svnTag, descriptor.prereleaseRepository);
            Archive.adjustChangesOpt(prerelease.checkout, prerelease.descriptor.project.version);
            prerelease.create(maven, propertyArgs);
            log.info("created prerelease in " + prerelease.target);
        } catch (Exception e) {
            target.scheduleRemove(log, "create failed: " + e.getMessage());
            throw e;
        }
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

    /**
     * Same as deployPrerelease, but the working directory is not the checkout of the prerelease. Instead, the original user checkout
     * (or a temporary checkout for bare commands) is used, and this, the pom file is the original snapshot pom file.
     *
     * Alternative implementations:
     * a) direct code with ArtifactDeployer
     *    - cannot use normal deploy configuration (e.g. addtional goal tied to the deploy phase)
     *    - cannot deploy more than one file at a time
     * b) deploy-file invocation
     *    - cannot use normal deploy configuration (e.g. addtional goal tied to the deploy phase)
     */
    public void deploySnapshot(Maven maven, Log log, Map<String, String> userProperties, MavenProject originalProject) throws Exception {
        maven.deploySnapshot(checkout.getWorld().file(originalProject.getFile()).getParent(), log, userProperties, this);
    }

    public void commit(Log log, String commitMessage) throws Failure {
        Launcher launcher;

        log.info("committing tag:");
        launcher = Subversion.launcher(checkout, "commit", "-m", commitMessage);
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

    public FileNode prepareOrigCommit(Log log) throws IOException, XmlException, SAXException, MojoExecutionException {
        FileNode result;
        ChangesXml changes;

        result = checkout.getWorld().getTemp().createTempDirectory();
        Subversion.sparseCheckout(log, result, descriptor.svnOrig, "HEAD", true);
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

        Transform.adjustPom(result.join("pom.xml"), descriptor.previous, descriptor.next, null, null, null);
        if (changes != null) {
            changes.releaseDate(descriptor.project.version, new Date());
            changes.save();
        }
        // svn up does not fail for none-existing files!
        return result;
    }

    //--

    public void create(Maven maven, Map<String, String> propertyArgs) throws Exception {
        FileNode installed;

        // no "clean" because we have a vanilla directory from svn
        try {
            maven.build(checkout, descriptor.releaseProps(propertyArgs), new PrepareExecutionListener(this, maven.getExecutionListener()),
                    FilteringMojoExecutor.NONE_CHECK, descriptor.prereleaseRepository, "install");
        } finally {
            installed = descriptor.project.localRepo(maven);
            if (installed.exists()) {
                installed.move(artifacts());
            }
        }
        // TODO: check that the workspace is without modifications
    }

    public void check(Log log, Map<String, String> propertyArgs, Maven maven) throws Exception {
        log.info("prerelease checks for " + descriptor.project);
        maven.build(checkout, descriptor.releaseProps(propertyArgs), FilteringMojoExecutor.CHECK, descriptor.prereleaseRepository, "install");
    }

    //-- promote

    public void promote(Log log, Map<String, String> propertyArgs, String createTagMessage, String revertTagMessage, String nextIterationMessage, Maven maven) throws Exception {
        FileNode origCommit;

        check(log, propertyArgs, maven);
        log.info("promoting revision " + descriptor.revision + " to " + descriptor.project);
        origCommit = prepareOrigCommit(log);
        try {
            promoteLocked(log, propertyArgs, createTagMessage, revertTagMessage, nextIterationMessage, origCommit, maven);
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
    private void promoteLocked(Log log, Map<String, String> propertyArgs, String commitTagMessage, String revertTagMessage, String commitNextMessage,
            FileNode origCommit, Maven maven) throws Exception {
        FileNode installed;

        commit(log, renderMessage(commitTagMessage));
        try {
            maven.deployPrerelease(log, propertyArgs, this);
        } catch (Exception e) {
            log.info("deployment failed - reverting tag");
            revertCommit(log, renderMessage(revertTagMessage));
            target.scheduleRemove(log, "deployment failed (tag has been reverted): " + e.getMessage());
            throw e;
        }

        // local install
        installed = descriptor.project.localRepo(maven);
        installed.deleteTreeOpt();
        artifacts().move(installed);

        try {
            log.info("Update pom and changes ...");
            log.debug(Subversion.launcher(origCommit, "commit", "-m", renderMessage(commitNextMessage)).exec());
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

    public String renderMessage(String message) throws SubstitutionException {
        Map<String, String> variables;

        variables = new HashMap<>();
        variables.put("revision", Long.toString(descriptor.revision));
        variables.put("release", descriptor.project.toString());
        return Substitution.ant().apply(message, variables);
    }

    //--

    public void artifactFiles(MavenProject project, MavenProjectHelper projectHelper) throws IOException {
        FileNode file;
        String type;
        String classifier;
        String[] tmp;

        for (Map.Entry<FileNode, String[]> entry : artifactFiles().entrySet()) {
            file = entry.getKey();
            tmp = entry.getValue();
            classifier = tmp[0];
            type = tmp[1];
            if ("pom".equals(type) && !project.getPackaging().equals("pom")) {
                // ignored
            } else {
                if (classifier == null) {
                    project.getArtifact().setFile(file.toPath().toFile());
                } else {
                    projectHelper.attachArtifact(project, type, classifier, file.toPath().toFile());
                }
            }
        }
    }

    /** @return Mapping of Artifact File to [ classifier, type ]. Classifier is null for the main artifact */
    public Map<FileNode, String[]> artifactFiles() throws IOException {
        FileNode artifacts;
        String name;
        String str;
        String type;
        String classifier;
        Map<FileNode, String[]> result;

        artifacts = artifacts();
        result = new HashMap<>();
        for (FileNode file : artifacts.list()) {
            name = file.getName();
            if (name.endsWith(".md5") || name.endsWith(".sha1") || name.endsWith(".asc")
                    || /* maven 3.0 */ name.equals("_maven.repositories") || /* maven 3.1 */ name.equals("_remote.repositories")) {
                // skip
            } else {
                type = file.getExtension();
                str = name.substring(0, name.length() - type.length() - 1);
                str = Strings.removeLeft(str, descriptor.project.artifactId + "-");
                str = Strings.removeLeft(str, descriptor.project.version);
                if (str.isEmpty()) {
                    classifier = null;
                } else {
                    classifier = Strings.removeLeft(str, "-");
                }
                result.put(file, new String[] { classifier, type });
            }
        }
        return result;
    }
}
