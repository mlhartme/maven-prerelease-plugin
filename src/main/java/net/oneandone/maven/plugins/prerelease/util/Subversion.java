package net.oneandone.maven.plugins.prerelease.util;

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;

public final class Subversion {
    public static Launcher launcher(FileNode dir, String ... args) {
        return new Launcher(dir, "svn", "--non-interactive", "--no-auth-cache").arg(args);
    }

    public static boolean exists(FileNode dir, String svnurl) {
        try {
            Subversion.launcher(dir, "ls", svnurl).exec();
            return true;
        } catch (Failure e) {
            return false;
        }
    }

    private Subversion() {
    }
}
