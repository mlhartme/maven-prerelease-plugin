package com.oneandone.devel.maven.plugins.prerelease.core;

import com.oneandone.devel.cli.change.File;
import com.oneandone.devel.maven.plugins.prerelease.OnShutdown;
import net.oneandone.sushi.fs.DirectoryNotFoundException;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.ListException;
import net.oneandone.sushi.fs.MkfileException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.xml.XmlException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Date;

/* Manages prereleases for one given groupId/artifactId.
 * Directory layout:
 *   groupId/artifactId.LOCK  <- optional
 *   groupId/artifactId/      <- archive.directory
 *     |- revision1           <- prerelease directory, ready to promote; promoting the prerelease removes this directory
 *     |     |- tags
 *     |     |    - <tagname>
 *     |     |- artifacts
 *     |      - prerelease.properties
 *     |- revision2
 *     :
 *     |- REMOVE              <- a renamed prerelease directory
 *     :     :                   optional - only when the last create call failed (because the mvn call failed) or promote succeeded
 *     :     |- CAUSE         <- why this directory is to be removed
 *     :     :
 */
public class Archive implements AutoCloseable {
    public static FileNode directory(FileNode archiveRoot, MavenProject project) {
        return archiveRoot.join(project.getGroupId(), project.getArtifactId());
    }

    public static Archive open(FileNode directory, int timeout, Log log) throws IOException {
        Archive archive;

        archive = new Archive(directory);
        archive.open(timeout, log);
        return archive;
    }

    public static long latest(FileNode directory) throws ListException, DirectoryNotFoundException {
        long revision;
        long result;

        result = -1;
        if (directory.exists()) {
            for (FileNode prerelease : directory.list()) {
                try {
                    revision = Long.parseLong(prerelease.getName());
                } catch (NumberFormatException e) {
                    // not a prerelease
                    continue;
                }
                if (revision > result) {
                    result = revision;
                }
            }
        }
        return result;
    }

    public final FileNode directory;
    private boolean opened = false;
    private boolean closed = false;

    private Archive(FileNode directory) {
        this.directory = directory;
    }

    public Target target(long revision) {
        return new Target(directory.join(Long.toString(revision)), revision);
    }

    //--

    private FileNode lockFile() {
        return directory.getParent().join(directory.getName() + ".LOCK");
    }

    /** @param timeout in seconds */
    private void open(int timeout, Log log) throws IOException {
        FileNode file;
        int seconds;

        if (opened) {
            throw new IllegalStateException();
        }
        file = lockFile();
        try {
            seconds = 0;
            while (true) {
                // every time - if someone wiped the whole prereleases directory
                file.getParent().mkdirsOpt();
                try {
                    file.mkfile();
                    OnShutdown.get().deleteAtExit(file);
                    opened = true;
                    file.writeString(Integer.toString(pid()));
                    log.debug("locked for pid " + pid());
                    return;
                } catch (MkfileException e) {
                    if (seconds > timeout) {
                        log.warn("Lock timed out after " + seconds + "s.");
                        throw e;
                    }
                    if (seconds % 10 == 0) {
                        log.info("Waiting for " + file + ": " + seconds + "s");
                        log.debug(e);
                    }
                    seconds++;
                    Thread.sleep(1000);
                }
            }
        } catch (InterruptedException e) {
            log.warn("interrupted");
        }
    }

    @Override
    public void close() throws Exception {
        FileNode file;

        if (!opened) {
            throw new IllegalStateException("not opened");
        }
        if (closed) {
            throw new IllegalStateException("already closed");
        }
        file = lockFile();
        file.deleteFile();
        // because another thread waiting for this lock might create this file again.
        // The shutdown hook must not delete the file created by this other thread.
        OnShutdown.get().dontDeleteAtExit(file);
        closed = true;
    }

    //--

    private static int pid = 0;

    public static int pid() {
        String str;
        int idx;

        if (pid == 0) {
            // see http://blog.igorminar.com/2007/03/how-java-application-can-discover-its.html?m=1
            str = ManagementFactory.getRuntimeMXBean().getName();
            idx = str.indexOf('@');
            if (idx == -1) {
                throw new IllegalStateException("cannot guess pid from " + str);
            }
            pid = Integer.parseInt(str.substring(0, idx));
        }
        return pid;
    }

    /** @return true to indicate missing actions */
    public static boolean adjustChanges(FileNode workingCopy, String version) throws XmlException, IOException, SAXException {
        File changes;

        try {
            changes = File.load(workingCopy);
        } catch (FileNotFoundException e) {
            return false;
        }
        changes.releaseDate(version, new Date());
        changes.save();
        return changes.actions(version) == 0;
    }
}
