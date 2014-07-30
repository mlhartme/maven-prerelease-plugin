package net.oneandone.maven.plugins.prerelease.util;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PrereleaseRepository implements WorkspaceReader {
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
