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

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.project.MavenProject;

public class Project {
    public final String name;
    public final String url;
    public final String groupId;
    public final String artifactId;
    public final String version;

    public static Project forMavenProject(MavenProject project) {
        return forMavenProject(project, project.getVersion());
    }

    public static Project forMavenProject(MavenProject project, String version) {
        return new Project(project.getName(), project.getUrl(), project.getGroupId(), project.getArtifactId(), version);
    }

    public Project(String name, String url, String groupId, String artifactId, String version) {
        this.name = name;
        this.url = url;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    // TODO: use some maven component for this
    public FileNode localRepo(World world) {
        return (FileNode) world.getHome().join(".m2/repository", groupId.replace('.', '/'), artifactId, version);
    }

    public String toString() {
        return groupId + ":" + artifactId + ":" + version;
    }
}
