package net.oneandone.maven.plugins.prerelease.util;

import net.oneandone.maven.plugins.prerelease.core.Archive;
import net.oneandone.maven.plugins.prerelease.core.Descriptor;
import net.oneandone.maven.plugins.prerelease.core.Prerelease;
import net.oneandone.maven.plugins.prerelease.core.Target;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PrereleaseRepository implements WorkspaceReader {
    public static PrereleaseRepository forProject(MavenProject mavenProject, List<FileNode> storages) throws IOException {
        PrereleaseRepository result;
        Prerelease prerelease;
        FileNode file;
        String[] tmp;
        String classifier;
        String type;
        Artifact artifact;

        // TODO: expensive
        // TODO: duplicate versions
        result = new PrereleaseRepository();
        for (Dependency dependency : mavenProject.getDependencies()) {
            if (Descriptor.isSnapshot(dependency.getVersion())) {
                try (Archive archive = Archive.open(Archive.directories(storages, dependency.getGroupId(), dependency.getArtifactId()), 1 /* TODO */, null)) {
                    for (Map.Entry<Long, FileNode> foo : archive.list().entrySet()) {
                        prerelease = Prerelease.load(new Target(foo.getValue(), foo.getKey()));
                        for (Map.Entry<FileNode, String[]> entry : prerelease.artifactFiles().entrySet()) {
                            file = entry.getKey();
                            tmp = entry.getValue();
                            classifier = tmp[0];
                            type = tmp[1];
                            artifact = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(), classifier, type, prerelease.descriptor.project.version);
                            artifact.setFile(file.toPath().toFile());
                            result.add(artifact);
                        }
                    }
                }
            }
        }
        return result;
    }

    //--

    private List<Artifact> files;

    private final WorkspaceRepository repository;

    public PrereleaseRepository() {
        repository = new WorkspaceRepository("prereleases");
        files = new ArrayList<>();
    }

    public void add(Artifact artifact) {
        if (artifact.getFile() == null || !artifact.getFile().isFile()) {
            throw new IllegalArgumentException(artifact.toString());
        }
        files.add(artifact);
    }

    @Override
    public WorkspaceRepository getRepository() {
        return repository;
    }

    @Override
    public File findArtifact(Artifact artifact) {
        for (Artifact candidate : files) {
            // TODO
            if (candidate.getGroupId().equals(artifact.getGroupId()) && candidate.getArtifactId().equals(artifact.getArtifactId())
                  && candidate.getVersion().equals(artifact.getVersion())) {
                return candidate.getFile();
            }
        }
        return null;
    }

    @Override
    public List<String> findVersions(Artifact artifact) {
        List<String> result;

        result = new ArrayList<>();
        for (Artifact candidate : files) {
            if (candidate.getGroupId().equals(artifact.getGroupId()) && candidate.getArtifactId().equals(artifact.getArtifactId())) {
                result.add(candidate.getVersion());
            }
        }
        return result;
    }
}
