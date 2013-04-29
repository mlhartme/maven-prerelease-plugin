package net.oneandone.maven.plugins.prerelease.core;

import com.oneandone.devel.cli.change.File;
import com.oneandone.devel.maven.Maven;
import net.oneandone.maven.plugins.prerelease.util.Mailer;
import net.oneandone.maven.plugins.prerelease.util.Subversion;
import net.oneandone.maven.plugins.prerelease.util.Transform;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Strings;
import net.oneandone.sushi.util.Util;
import net.oneandone.sushi.xml.XmlException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.deployment.DeploymentException;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.xml.sax.SAXException;

import javax.mail.MessagingException;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

    public static Prerelease create(Log log, Descriptor descriptor, Target target, boolean update, boolean promoting, Properties properties)
            throws Exception {
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
            prerelease.artifacts().mkdir();
            prerelease.descriptor.save(target);
            Transform.adjustPom(prerelease.checkout.join("pom.xml"), descriptor.previous, descriptor.project.version,
                    descriptor.svnOrig, descriptor.svnTag);
            Archive.adjustChanges(prerelease.checkout, prerelease.descriptor.project.version);
            prerelease.build(log, update, promoting, properties);
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

    public List<Artifact> artifactList() throws IOException {
        FileNode artifacts;
        FileNode dir;
        List<Artifact> result;
        Artifact artifact;

        artifacts = artifacts();
        dir = (FileNode) artifacts.findOne("**/*.pom").getParent();
        result = new ArrayList<>();
        for (FileNode file : dir.list()) {
            if (file.getName().endsWith(".md5") || file.getName().endsWith(".sha1") || file.getName().endsWith(".asc")) {
                // skip
            } else {
                artifact = artifact(file.getRelative(artifacts));
                artifact = artifact.setFile(file.toPath().toFile());
                result.add(artifact);
            }
        }
        return result;
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
        File changes;

        result = checkout.getWorld().getTemp().createTempDirectory();
        log.debug(target.svnLauncher("co", "--depth", "empty", descriptor.svnOrig, result.getAbsolute()).exec());
        log.debug(Subversion.launcher(result, "up", "pom.xml").exec());

        log.debug(Subversion.launcher(result, "up", "--depth", "empty", "src").exec());
        log.debug(Subversion.launcher(result, "up", "--depth", "empty", "src/changes").exec());
        log.debug(Subversion.launcher(result, "up", "src/changes/changes.xml").exec());
        try {
            changes = File.load(result);
        } catch (FileNotFoundException e) {
            log.info("no changes.xml to adjust.");
            changes = null;
        }

        Subversion.launcher(result, "lock", "pom.xml");
        if (changes != null) {
            Subversion.launcher(result, "lock", File.PATH);
        }

        // make sure the version we've locked what we will modify:
        // (or in other words: make sure we see possible changes that be committed between checkout and lock)
        Subversion.launcher(result, "up");

        Transform.adjustPom(result.join("pom.xml"), descriptor.previous, descriptor.next, null, null);
        if (changes != null) {
            changes.releaseDate(descriptor.project.version, new Date());
            changes.save();
        }
        // svn up does not fail if for none-existing files!
        return result;
    }

    //--

    public void deploy(Maven maven) throws IOException, DeploymentException {
        maven.deploy(descriptor.deployRepository, descriptor.deployPluginMetadata ? descriptor.project.name : null, artifactList());
    }

    //--

    public void build(final Log log, boolean alwaysUpdate, boolean promoting, Properties userProperties) throws Exception {
        Launcher mvn;

        mvn = net.oneandone.maven.plugins.prerelease.util.Maven.mvn(checkout, userProperties);
        mvn.arg("-DaltDeploymentRepository=prerelease::default::" + artifacts().toPath().toFile().toURI(),
                // do not install release artifacts because we do not necessarily deploy them
                "-Dmaven.install.skip",
                // no "clean" because we have a vanilla directory from svn
                "deploy");
        if (alwaysUpdate) {
            mvn.arg("-U");
        }
        mvn.args(promoting ? descriptor.schedule.promotingProperties() : descriptor.schedule.nonePromotingProperties());
        log.info(mvn.toString());
        mvn.exec(new LogWriter(log));

        // TODO: check that the workspace is without modifications
    }

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

    public void verify(final Log log, String profile, Properties userProperties) throws Exception {
        build(log, userProperties, "verify", /* to save disk space: */ "clean", "-P" + profile);
    }

    public void build(final Log log, Properties userProperties, String ... goals) throws Exception {
        Launcher mvn;

        mvn = net.oneandone.maven.plugins.prerelease.util.Maven.mvn(checkout, userProperties);
        mvn.arg(goals);
        log.info(mvn.toString());
        mvn.exec(new LogWriter(log));

        // TODO: check that the workspace is without modifications
    }

    //-- promote

    /**
     * @param problemMails may be null
     * @param beforePromotePhase true to include the beforePromotePhase, false when it was already included as part of the normal build
     * @param userProperties is mandatory for Jenkins builds, because the user name is passed as a property
     */
    public void promote(Log log, String problemMails, Maven maven, String user, boolean beforePromotePhase, Properties userProperties)
            throws Exception {
        FileNode origCommit;

        log.info("promoting revision " + descriptor.revision + " to " + descriptor.project);
        if (beforePromotePhase) {
            descriptor.schedule.beforePromote(log, checkout, userProperties);
        }
        origCommit = prepareOrigCommit(log);
        try {
            promoteLocked(log, problemMails, maven, user, userProperties, origCommit);
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
        if (origCommit.join(File.PATH).exists()) {
            Subversion.launcher(origCommit, "unlock" , File.PATH);
        }
    }

    private void promoteLocked(Log log, String problemMails, Maven maven, String user, Properties userProperties,
                               FileNode origCommit) throws IOException, DeploymentException, MessagingException {
        commit(log, user);
        try {
            deploy(maven);
        } catch (Exception e) {
            log.info("deployment failed - reverting tag");
            revertCommit(log, user);
            target.scheduleRemove(log, "deployment failed (tag has been reverted): " + e.getMessage());
            throw e;
        }
        try {
            descriptor.schedule.afterPromote(log, checkout, userProperties);
        } catch (Exception e) {
            log.warn("Promote succeeded: your artifacts have been deployed, and your svn tag was created. ");
            log.warn("However, after-promote jobs failed with this exception: ");
            log.warn(e);
            log.warn("Thus, you can use your release, but someone expecienced should have a look.");
            email(problemMails, e, descriptor.project.toString(), user);
        }
        try {
            log.info("Update pom and changes ...");
            log.debug(Subversion.launcher(origCommit, "commit", "-m", "Pre-Release " + descriptor.revision
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
            log.warn("Thus, you can use your release, but " + problemMails + " should have a look at this exception.");
            email(problemMails, e, descriptor.project.toString(), user);
        }
    }

    private static void email(String problemMails, Exception e, String name, String from) throws MessagingException {
        Mailer mailer;

        if (problemMails != null) {
            mailer = new Mailer();
            mailer.send(from, problemMails, "[prerelease failure] " + name, Util.toString(e));
        }
    }
}
