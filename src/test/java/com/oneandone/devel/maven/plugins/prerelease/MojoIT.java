package com.oneandone.devel.maven.plugins.prerelease;

import com.oneandone.devel.maven.plugins.prerelease.util.IntegrationBase;
import net.oneandone.sushi.fs.file.FileNode;
import org.junit.Test;

public class MojoIT extends IntegrationBase {
    @Test
    public void minimal() throws Exception {
        FileNode checkout;

        checkout = checkoutProject("minimal");
        mvn(checkout, "prerelease:status");
        mvn(checkout, "prerelease:create");
        mvn(checkout, "prerelease:update");
        mvn(checkout, "prerelease:update");
        mvn(checkout, "prerelease:promote");
    }
}
