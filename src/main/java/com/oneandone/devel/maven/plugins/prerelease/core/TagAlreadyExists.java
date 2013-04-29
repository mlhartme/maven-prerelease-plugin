package com.oneandone.devel.maven.plugins.prerelease.core;

public class TagAlreadyExists extends Exception {
    public final String tag;

    public TagAlreadyExists(String tag) {
        super("tag already exists: " + tag);
        this.tag = tag;
    }
}
