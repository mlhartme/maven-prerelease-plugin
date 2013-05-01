/**
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.maven.plugins.prerelease;

import net.oneandone.maven.plugins.prerelease.core.Prerelease;
import org.junit.Test;
import org.sonatype.aether.artifact.Artifact;

import static org.junit.Assert.assertEquals;

public class PrereleaseTest {
    @Test
    public void artifact() {
        check("net/oneandone/maven/plugins/prerelease/maven/plugins/prerelease/1.0.0/prerelease-1.0.0.jar",
                "net.oneandone.maven.plugins.prerelease.maven.plugins", "prerelease", "1.0.0", "jar", "");
        check("net/oneandone/maven/plugins/prerelease/maven/plugins/prerelease/1.0.0/prerelease-1.0.0-foo.jar",
                "net.oneandone.maven.plugins.prerelease.maven.plugins", "prerelease", "1.0.0", "jar", "foo");
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
