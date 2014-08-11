package net.oneandone.maven.plugins.prerelease.util;

import net.oneandone.maven.plugins.prerelease.core.Archive;
import net.oneandone.maven.plugins.prerelease.core.Descriptor;
import net.oneandone.maven.plugins.prerelease.core.Prerelease;
import net.oneandone.maven.plugins.prerelease.core.Project;
import net.oneandone.maven.plugins.prerelease.core.Storages;
import net.oneandone.maven.plugins.prerelease.core.Target;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.xml.Selector;
import net.oneandone.sushi.xml.XmlException;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PrereleaseRepository implements WorkspaceReader {
    public static PrereleaseRepository forDescriptor(String line, Storages storages) throws IOException {
        PrereleaseRepository result;
        String[] parts;
        String groupId;
        String artifactId;
        String version;
        long revision;

        result = new PrereleaseRepository();
        for (String entry : Separator.COMMA.split(line)) {
            parts = entry.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException(entry);
            }
            groupId = parts[0];
            artifactId = parts[1];
            version = parts[2];
            revision = Long.parseLong(parts[3]);
            Archive archive = storages.get(new Project(groupId, artifactId, version));
            result.add(Prerelease.load(archive.target(revision), storages));
        }
        return result;
    }

    public static PrereleaseRepository forProject(MavenProject mavenProject, Storages storages) throws IOException {
        PrereleaseRepository result;
        Prerelease prerelease;
        Archive archive;

        // TODO: expensive
        // TODO: handle multiple revisions
        result = new PrereleaseRepository();
        for (org.apache.maven.artifact.Artifact artifact : mavenProject.getArtifacts()) {
            if (Descriptor.isSnapshot(artifact.getVersion())) {
                if (artifact.getFile() == null) {
                    throw new IllegalStateException(artifact.toString());
                }
                archive = storages.get(new Project(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
                prerelease = archive.lookupArtifact(storages.getWorld().file(artifact.getFile()), storages);
                if (prerelease != null) {
                    result.add(prerelease);
                }
            }
        }
        return result;
    }

    //--

    private final List<Prerelease> prereleases;
    private final List<Artifact> artifacts;

    private final WorkspaceRepository repository;

    public PrereleaseRepository() {
        this.repository = new WorkspaceRepository("prereleases");
        this.prereleases = new ArrayList<>();
        this.artifacts = new ArrayList<>();
    }

    public boolean add(Prerelease prerelease) throws IOException {
        FileNode file;
        String[] tmp;
        Artifact artifact;

        for (Prerelease existing : prereleases) {
            if (prerelease.descriptor.project.equals(existing.descriptor.project)) {
                if (prerelease.target.getRevision() != existing.target.getRevision()) {
                    throw new IOException("conflicting pre-releases: " + prerelease.target.getRevision() + " vs " + existing.target.getRevision());
                }
                return false;
            }
        }
        prereleases.add(prerelease);
        for (Map.Entry<FileNode, String[]> entry : prerelease.artifactFiles().entrySet()) {
            file = entry.getKey();
            tmp = entry.getValue();
            artifact = new DefaultArtifact(prerelease.descriptor.project.groupId, prerelease.descriptor.project.artifactId,
                    tmp[0], tmp[1], prerelease.descriptor.project.version);
            artifact = artifact.setFile(file.toPath().toFile());
            artifacts.add(artifact);
        }
        return true;
    }

    @Override
    public WorkspaceRepository getRepository() {
        return repository;
    }

    @Override
    public File findArtifact(Artifact artifact) {
        Artifact candidate;

        candidate = lookup(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
        return candidate == null ? null : candidate.getFile();
    }

    public Artifact lookup(String groupId, String artifactId, String version) {
        for (Artifact candidate : artifacts) {
            // TODO
            if (candidate.getGroupId().equals(groupId) && candidate.getArtifactId().equals(artifactId) && (version == null || candidate.getVersion().equals(version))) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public List<String> findVersions(Artifact artifact) {
        List<String> result;

        result = new ArrayList<>();
        for (Artifact candidate : artifacts) {
            if (candidate.getGroupId().equals(artifact.getGroupId()) && candidate.getArtifactId().equals(artifact.getArtifactId())) {
                result.add(candidate.getVersion());
            }
        }
        return result;
    }

    public String toDescriptor() {
        StringBuilder builder;

        builder = new StringBuilder();
        for (Prerelease prerelease : prereleases) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(prerelease.descriptor.project.groupId);
            builder.append(':');
            builder.append(prerelease.descriptor.project.artifactId);
            builder.append(':');
            builder.append(prerelease.descriptor.project.version);
            builder.append(':');
            builder.append(prerelease.descriptor.revision);
        }
        return builder.toString();
    }

    public String toString() {
        return toDescriptor();
    }

    public void updateDependencies(Selector selector, Document document) throws XmlException {
        String artifactId;
        String groupId;
        Element version;
        Artifact artifact;

        for (Element dependency : selector.elements(document, "/M:project/M:dependencies/M:dependency")) {
            artifactId = selector.string(dependency, "artifactId");
            groupId = selector.string(dependency, "groupId");
            version = selector.element(dependency, "version");
            artifact = lookup(groupId, artifactId, null);
            if (artifact != null) {
                version.setTextContent(artifact.getVersion());
            }
        }
    }

    public List<Prerelease> nested() throws IOException {
        return prereleases;
    }
}
