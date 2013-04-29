package com.oneandone.devel.maven.plugins.prerelease.util;

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;

import java.util.Map;
import java.util.Properties;

public final class Maven {
    /** @param userProperties properties specified by the user with -D command line args. */
    public static Launcher mvn(FileNode basedir, Properties userProperties) {
        Launcher mvn;

        mvn = new Launcher(basedir, "mvn", "-B", /* same as mvn release plugin: */ "-DperformRelease");
        for (Map.Entry<Object, Object> entry : userProperties.entrySet()) {
            mvn.arg("-D" + entry.getKey() + "=" + entry.getValue());
        }
        return mvn;
    }

    private Maven() {
    }
}
