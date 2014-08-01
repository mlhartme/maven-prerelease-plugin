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
package net.oneandone.maven.plugins.prerelease.core;

import net.oneandone.maven.plugins.prerelease.util.Maven;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.project.MavenProject;

public class Project {
    public final String groupId;
    public final String artifactId;
    public final String version;

    public static Project forMavenProject(MavenProject project) {
        return forMavenProject(project, project.getVersion());
    }

    public static Project forMavenProject(MavenProject project, String version) {
        return new Project(project.getGroupId(), project.getArtifactId(), version);
    }

    public Project(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public FileNode localRepo(Maven maven) {
        return maven.getLocalRepositoryDir().join(groupId.replace('.', '/'), artifactId, version);
    }

    public String toString() {
        return groupId + ":" + artifactId + ":" + version;
    }

    @Override
    public boolean equals(Object obj) {
        Project project;

        if (obj instanceof Project) {
            project = (Project) obj;
            return (artifactId.equals(project.artifactId) && groupId.equals(project.groupId) && version.equals(project.version));
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int result;

        result = groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }
}
