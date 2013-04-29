/* Copyright (c) 1&1. All Rights Reserved. */

package com.oneandone.devel.maven.plugins.prerelease.frischfleisch;

public class FrischfleischException extends Exception {
    public FrischfleischException(String msg) {
        super(msg);
    }

    public FrischfleischException(String msg, Exception e) {
        super(msg, e);
    }
}
