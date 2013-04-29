/* Copyright (c) 1&1. All Rights Reserved. */

package com.oneandone.devel.maven.plugins.prerelease.frischfleisch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ContentTest {
    private Content data;

    @Test
    public void illegalNull() {
        data = new Content();
        try {
            data.header("foo", null);
            fail();
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalNewline() {
        data = new Content();
        data.header("foo", "1\n2");
    }

    @Test
    public void toStrinG() {
        data = new Content();
        data.header("a", "b");
        data.body("abc");
        assertEquals("a: b\nabc\n", data.toString());
    }
}
