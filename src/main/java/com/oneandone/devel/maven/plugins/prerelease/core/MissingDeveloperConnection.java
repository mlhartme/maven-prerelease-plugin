package com.oneandone.devel.maven.plugins.prerelease.core;

public class MissingDeveloperConnection extends Exception {
    public MissingDeveloperConnection() {
        super("missing scm/developerConnection");
    }
}
