package net.oneandone.maven.plugins.prerelease.core;

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

    public String toString() {
        return groupId + ":" + artifactId + ":" + version;
    }
}
