package net.oneandone.maven.plugins.prerelease.core;

public class CannotBumpVersion extends Exception {
    public final String version;

    public CannotBumpVersion(String version) {
        super("cannot bump version: " + version);

        this.version = version;
    }

    public CannotBumpVersion(String version, NumberFormatException e) {
        this(version);

        initCause(e);
    }
}
