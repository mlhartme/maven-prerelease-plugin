package com.oneandone.devel.maven.plugins.prerelease.core;

public class RevisionMismatch extends Exception {
    public RevisionMismatch(long workspaceRevision, long svnRevision) {
        super("revision mismatch: workspace " + workspaceRevision + " vs svn " + svnRevision);
    }
}
