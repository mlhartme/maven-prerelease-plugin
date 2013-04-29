/* Copyright (c) 1&1. All Rights Reserved. */

package net.oneandone.maven.plugins.prerelease;

import com.oneandone.devel.devreg.model.Registry;
import com.oneandone.devel.devreg.model.UnknownUserException;
import com.oneandone.devel.maven.Maven;
import net.oneandone.maven.plugins.prerelease.core.Descriptor;
import net.oneandone.maven.plugins.prerelease.core.Prerelease;
import net.oneandone.maven.plugins.prerelease.core.Target;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;

/**
 * Perform update without a working copy. Svn url and revision are passed as arguments, not determined from a working copy.
 */
@Mojo(name = "bare-update", requiresProject = false)
public class BareUpdate extends BareBase {
    /**
     * Email of the user invoking this goal. Determined via devreg when not specified.
     */
    @Parameter(property = "prerelease.user", defaultValue = "")
    private String user;

    public String getUser() throws IOException, UnknownUserException {
        Registry registry;

        if (user == null || user.isEmpty()) {
            registry = Registry.loadCached(world);
            return registry.whoAmI().getEmail();
        } else {
            return user;
        }
    }

    public void doExecute(Maven maven, MavenProject project, Target target, Descriptor descriptor) throws Exception {
        Prerelease prerelease;

        // TODO: duplicated code from update Mojo ...
        if (target.exists()) {
            getLog().info("prerelease already exists: " + descriptor.getName());
        } else {
            prerelease = Prerelease.create(getLog(), descriptor, target, alwaysUpdate, false, session.getUserProperties());
            try {
                descriptor.check(world, project);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                prerelease.target.scheduleRemove(getLog(), "build ok, but prerelease is not promotable: " + e.getMessage());
            }
        }
    }
}
