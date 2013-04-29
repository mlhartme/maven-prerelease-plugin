package net.oneandone.maven.plugins.prerelease.core;

import com.oneandone.devel.maven.Maven;
import net.oneandone.maven.plugins.prerelease.util.IntegrationBase;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WorkingCopyIT extends IntegrationBase {
    @Test
    public void noChanges() throws Exception {
        WorkingCopy workingCopy;

        workingCopy = WorkingCopy.load(checkoutProject("minimal"));
        workingCopy.check();
    }

    public void otherChanges() throws Exception {
        long initialRevision;
        FileNode mine;
        FileNode other;
        WorkingCopy workingCopy;

        mine = checkoutProject("minimal");
        initialRevision = WorkingCopy.load(mine).revision();
        other = checkoutProject("minimal", "other");
        append(other.join("pom.xml"), "<!-- bla -->");
        svnCommit(other, "other change");

        workingCopy = WorkingCopy.load(mine);
        workingCopy.check();
        assertEquals(initialRevision, workingCopy.revision());
    }

    @Test(expected = UncommitedChanges.class)
    public void localModification() throws Exception {
        FileNode dir;
        WorkingCopy workingCopy;

        dir = checkoutProject("minimal");
        append(dir.join("pom.xml"), "<!-- bla -->");
        workingCopy = WorkingCopy.load(dir);
        workingCopy.check();
    }

    @Test(expected = PendingUpdates.class)
    public void pendingUpdate() throws Exception {
        FileNode mine;
        FileNode other;
        WorkingCopy workingCopy;

        mine = checkoutProject("minimal");

        other = checkoutProject("minimal", "other");
        append(other.join("pom.xml"), "<!-- bla -->");
        svnCommit(other, "other change");

        append(mine.join("second.txt"), "\nbla");
        svnCommit(mine, "my change");

        workingCopy = WorkingCopy.load(mine);
        workingCopy.check();
    }


    @Test(expected = SvnUrlMismatch.class)
    public void svnmissmatch() throws Exception {
        FileNode dir;
        Maven maven;
        MavenProject project;
        long revision;
        Descriptor descriptor;

        dir = checkoutProject("svnmismatch");
        maven = Maven.withDefaults(WORLD);
        project = maven.loadPom(dir.join("pom.xml"));
        revision = WorkingCopy.load(dir).revision(); // TODO: expensive
        descriptor = Descriptor.checkedCreate(WORLD, project, revision, new Schedule());
        WorkingCopy.load(dir).checkCompatibility(descriptor);
    }

    @Test(expected = RevisionMismatch.class)
    public void revisionmissmatch() throws Exception {
        FileNode dir;
        Maven maven;
        MavenProject project;
        long revision;
        Descriptor descriptor;

        dir = checkoutProject("minimal");
        maven = Maven.withDefaults(WORLD);
        project = maven.loadPom(dir.join("pom.xml"));
        revision = WorkingCopy.load(dir).revision() + 1;
        descriptor = Descriptor.checkedCreate(WORLD, project, revision, new Schedule());
        WorkingCopy.load(dir).checkCompatibility(descriptor);
    }
}
