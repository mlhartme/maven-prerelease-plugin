package net.oneandone.maven.plugins.prerelease;

import net.oneandone.maven.plugins.prerelease.util.Transform;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TransformTest {
    @Test
    public void scm() throws MojoExecutionException {
        assertEquals("abc/replaced/foo", Transform.adjustScm("abc/trunk/foo", "/trunk/foo", "/replaced/foo"));
        assertEquals("abc/replaced/foo", Transform.adjustScm("abc/trunk", "/trunk", "/replaced/foo"));
        assertEquals("abc/replaced/foo", Transform.adjustScm("abc/branches/bar", "/branches/bar", "/replaced/foo"));
    }
}
