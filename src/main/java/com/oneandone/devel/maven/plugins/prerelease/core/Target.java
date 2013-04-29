package com.oneandone.devel.maven.plugins.prerelease.core;

import com.oneandone.devel.maven.plugins.prerelease.util.Subversion;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;

/**
 * Directory for a prerelease. The prerelease is not neccessarily promotable (it might not exist (yet), or it may be broken, or it
 * might already be promoted).
 */
public class Target {
    private FileNode directory;
    private final long revision;

    public Target(FileNode directory, long revision) {
        this.directory = directory;
        this.revision = revision;
    }

    public boolean exists() {
        return directory.exists();
    }

    public Prerelease loadOpt() throws IOException {
        return exists() ? Prerelease.load(this) : null;
    }

    public FileNode join(String ... paths) {
        return directory.join(paths);
    }

    public void scheduleRemove(Log log, String message) throws IOException {
        FileNode remove;

        remove = removeDirectory();
        log.info(message + " - moving prerelease to " + remove);
        directory.move(remove);
        remove.join("CAUSE").writeString(message);
        directory = remove;
    }

    public void removeOthers() throws IOException {
        for (FileNode prerelease : directory.getParent().list()) {
            if (!directory.equals(prerelease)) {
                prerelease.deleteTree();
            }
        }
    }

    public long getRevision() {
        return revision;
    }

    public void create() throws IOException {
        removeDirectory().deleteTreeOpt();
        directory.mkdirs();
    }

    public Launcher svnLauncher(String ... args) {
        return Subversion.launcher(directory, args);
    }

    private FileNode removeDirectory() {
        return directory.getParent().join("REMOVE");
    }

    public boolean checkoutLinkOpt(String path) throws IOException {
        if (path == null) {
            return false;
        }
        path = path.trim();
        if (path.isEmpty()) {
            return false;
        }
        return checkoutLink(directory.getWorld().file(path));
    }

    /** @return true when created */
    public boolean checkoutLink(FileNode checkoutLink) throws IOException {
        FileNode tags;
        FileNode workingCopy;

        tags = directory.join("tags");
        if (!tags.exists()) {
            return false;
        }
        workingCopy = null;
        for (FileNode file : tags.list()) {
            if (file.isDirectory() && !file.getName().startsWith(".")) {
                if (workingCopy != null) {
                    throw new IllegalStateException();
                }
                workingCopy = file;
            }
        }
        if (workingCopy == null) {
            return false;
        }
        checkoutLink.deleteTreeOpt();
        checkoutLink.getParent().mkdirsOpt();
        workingCopy.link(checkoutLink);
        return true;
    }

    public String toString() {
        return directory.toString();
    }
}
