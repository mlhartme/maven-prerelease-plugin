package net.oneandone.maven.plugins.prerelease;

import net.oneandone.maven.plugins.prerelease.core.Prerelease;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Invoke all plugins attached to the deploy phase. Uses internally by the Promote Mojo. */

@Mojo(name = "do-checkpoint")
public class DoCheckpoint extends Base {
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /** TODO */
    private static Map<String, String> previous;

    @Override
    public void doExecute() throws Exception {
        String old;
        Prerelease prerelease;
        Map<String, String> deployProperties;

        if (previous == null) {
            previous = new HashMap<>();
            for (Map.Entry<Object, Object> entry : project.getProperties().entrySet()) {
                if (entry.getValue() instanceof String) {
                    previous.put((String) entry.getKey(), (String) entry.getValue());
                } else {
                    getLog().warn("not a string: " + entry.getKey() + ": " + entry.getValue());
                }
            }
        } else {
            prerelease = Prerelease.forCheckout(world.file(project.getBasedir()));
            deployProperties = prerelease.descriptor.deployProperties;
            for (Map.Entry<Object, Object> entry : project.getProperties().entrySet()) {
                if (entry.getValue() instanceof String) {
                    old = previous.get(entry.getKey());
                    if (!entry.getValue().equals(old)) {
                        deployProperties.put((String) entry.getKey(), (String) entry.getValue());
                    }
                }
            }
            getLog().debug("writing " + deployProperties);
            prerelease.descriptor.save(prerelease.target);
        }
    }
}
