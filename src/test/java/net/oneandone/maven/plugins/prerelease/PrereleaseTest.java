package net.oneandone.maven.plugins.prerelease;

import net.oneandone.maven.plugins.prerelease.core.Prerelease;
import org.junit.Test;
import org.sonatype.aether.artifact.Artifact;

import static org.junit.Assert.assertEquals;

public class PrereleaseTest {
    @Test
    public void artifact() {
        check("com/oneandone/devel/maven/plugins/prerelease/1.0.0/prerelease-1.0.0.jar",
                "com.oneandone.devel.maven.plugins", "prerelease", "1.0.0", "jar", "");
        check("com/oneandone/devel/maven/plugins/prerelease/1.0.0/prerelease-1.0.0-foo.jar",
                "com.oneandone.devel.maven.plugins", "prerelease", "1.0.0", "jar", "foo");
    }

    private void check(String path, String groupId, String artifactId, String version, String extension, String classifier) {
        Artifact artifact;

        artifact = Prerelease.artifact(path);
        assertEquals(groupId, artifact.getGroupId());
        assertEquals(artifactId, artifact.getArtifactId());
        assertEquals(version, artifact.getVersion());
        assertEquals(extension, artifact.getExtension());
        assertEquals(classifier, artifact.getClassifier());
    }
}
