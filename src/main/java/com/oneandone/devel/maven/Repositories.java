package com.oneandone.devel.maven;

import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.RepositoryPolicy;

import java.util.Arrays;
import java.util.List;

public final class Repositories {
    public static RemoteRepository create(String id, String url, String snapshotUpdates, String releaseUpdates) {
        RemoteRepository repository;

        repository = new RemoteRepository(id, "default", url);
        repository.setPolicy(true, new RepositoryPolicy(snapshotUpdates != null, snapshotUpdates, RepositoryPolicy.CHECKSUM_POLICY_WARN));
        repository.setPolicy(false, new RepositoryPolicy(releaseUpdates != null, releaseUpdates, RepositoryPolicy.CHECKSUM_POLICY_WARN));
        return repository;
    }

    public static final RemoteRepository EXT_RELEASES
            = create("central", "http://mavenrepo.united.domain:8081/nexus/content/groups/ext-releases",
                     null, RepositoryPolicy.UPDATE_POLICY_NEVER);
    public static final RemoteRepository GROUP_RELEASES
            = create("1und1-releases", "http://mavenrepo.united.domain:8081/nexus/content/groups/1und1-releases",
                     null, RepositoryPolicy.UPDATE_POLICY_NEVER);
    public static final RemoteRepository GROUP_SNAPSHOTS
            = create("1und1-snapshots", "http://mavenrepo.united.domain:8081/nexus/content/groups/1und1-snapshots",
                     RepositoryPolicy.UPDATE_POLICY_DAILY, null);

    // this group is weired, because it contains both the 1und1-plugins and 1und1-plugin-snapshots ...
    public static final RemoteRepository GROUP_PLUGINS
            = create("1und1-plugins", "http://mavenrepo.united.domain:8081/nexus/content/groups/1und1-plugins",
                     null, RepositoryPolicy.UPDATE_POLICY_NEVER);

    public static final List<RemoteRepository> STANDARD_READ = Arrays.asList(EXT_RELEASES, GROUP_RELEASES, GROUP_SNAPSHOTS, GROUP_PLUGINS);

    public static final RemoteRepository RELEASES_WRITE
            = create("1und1-releases", "http://mavenrepo.united.domain:8081/nexus/content/repositories/1und1-stable",
                     null, RepositoryPolicy.UPDATE_POLICY_NEVER);

    public static final List<RemoteRepository> STANDARD_WRITE = Arrays.asList(RELEASES_WRITE);

    private Repositories() {
    }
}
