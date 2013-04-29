package net.oneandone.maven.plugins.prerelease.core;

public class SvnUrlMismatch extends Exception {
    public SvnUrlMismatch(String workspaceUrl, String pomUrl) {
        super("svn url mismatch: workspace specifies " + workspaceUrl + ", but pom.xml specifies " + pomUrl);
    }
}
