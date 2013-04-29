package com.oneandone.devel.maven.plugins.prerelease.core;

public class CannotDeterminTagBase extends Exception {
    public final String svnurl;

    public CannotDeterminTagBase(String svnurl) {
        super("cannot determin tagbase from developer connection " + svnurl);

        this.svnurl = svnurl;
    }
}
