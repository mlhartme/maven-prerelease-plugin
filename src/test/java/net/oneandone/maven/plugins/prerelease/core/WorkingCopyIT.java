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

import static org.junit.Assert.assertEquals;

import org.apache.maven.project.MavenProject;
import org.junit.Test;

import net.oneandone.maven.plugins.prerelease.util.IntegrationBase;
import net.oneandone.maven.plugins.prerelease.util.Maven;
import net.oneandone.maven.plugins.prerelease.util.Subversion;
import net.oneandone.sushi.fs.file.FileNode;

public class WorkingCopyIT extends IntegrationBase {
    @Test
    public void noChanges() throws Exception {
        WorkingCopy workingCopy;

        workingCopy = WorkingCopy.load(checkoutProject("minimal"), Subversion.SvnCredentials.NONE());
        workingCopy.check();
    }

    @Test
    public void otherChanges() throws Exception {
        long initialRevision;
        FileNode mine;
        FileNode other;
        WorkingCopy workingCopy;

        mine = checkoutProject("minimal");
        initialRevision = WorkingCopy.load(mine, Subversion.SvnCredentials.NONE()).revision();
        other = checkoutProject("minimal", "other");
        append(other.join("pom.xml"), "<!-- bla -->");
        svnCommit(other, "other change");

        workingCopy = WorkingCopy.load(mine, Subversion.SvnCredentials.NONE());
        workingCopy.check();
        assertEquals(initialRevision, workingCopy.revision());
    }

    @Test(expected = UncommitedChanges.class)
    public void localModification() throws Exception {
        FileNode dir;
        WorkingCopy workingCopy;

        dir = checkoutProject("minimal");
        append(dir.join("pom.xml"), "<!-- bla -->");
        workingCopy = WorkingCopy.load(dir, Subversion.SvnCredentials.NONE());
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

        workingCopy = WorkingCopy.load(mine, Subversion.SvnCredentials.NONE());
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
        maven = maven(WORLD);
        project = maven.loadPom(dir.join("pom.xml"));
        revision = WorkingCopy.load(dir, Subversion.SvnCredentials.NONE()).revision();
        descriptor = Descriptor.checkedCreate(WORLD, "foo", project, revision, false, true, Subversion.SvnCredentials.NONE());
        WorkingCopy.load(dir, Subversion.SvnCredentials.NONE()).checkCompatibility(descriptor);
    }

    @Test(expected = RevisionMismatch.class)
    public void revisionmissmatch() throws Exception {
        FileNode dir;
        Maven maven;
        MavenProject project;
        long revision;
        Descriptor descriptor;

        dir = checkoutProject("minimal");
        maven = maven(WORLD);
        project = maven.loadPom(dir.join("pom.xml"));
        revision = WorkingCopy.load(dir, Subversion.SvnCredentials.NONE()).revision() + 1;
        descriptor = Descriptor.checkedCreate(WORLD, "foo", project, revision, false, true, Subversion.SvnCredentials.NONE());
        WorkingCopy.load(dir, Subversion.SvnCredentials.NONE()).checkCompatibility(descriptor);
    }
}
