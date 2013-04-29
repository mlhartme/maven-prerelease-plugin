package net.oneandone.maven.plugins.prerelease.core;

public class MissingScmTag extends Exception {
    public MissingScmTag() {
        super("missing scm tag");
    }
}
