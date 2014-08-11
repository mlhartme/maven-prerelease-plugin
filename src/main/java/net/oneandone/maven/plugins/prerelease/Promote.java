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

import net.oneandone.maven.plugins.prerelease.core.Archive;
import net.oneandone.maven.plugins.prerelease.core.Prerelease;
import net.oneandone.maven.plugins.prerelease.core.WorkingCopy;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Promotes a prerelease by commiting the tag and deploying its artifact(s).
 *
 * Execute this goal in the svn working directory of the project you want to release and make sure you have created a prerelease.
 *
 * This goal commits the tag of the current prerelease and deploys the respective artifact(s). It also updates (in your svn trunk or branch
 * on the svn server) the project version in pom.xml to the next development version, and it updates your changes file (if you have one).
 * In your svn working directory, it runs "svn up" to get the adjusted pom and changes files.
 *
 * Artifacts are deployed by invoking all plugin goals tied to the deploy phase. The first goal is mandatory, because it's configured by
 * the packaging. If it fails, promotion fails. All other goals (e.g. email notifications) are optional: they may fail (yielding the
 * respective warning), but they don't cause the promote goal to fail.
 *
 * Error handling. In contrast to Maven's Release Plugin there's no need for a rollback goal: when promote fails, artifacts and tags will
 * be properly removed. (If Tag creation fails, this goal fails with an error. If artifact deployment fails, the svn tag will be deleted
 * from the repository and the goal fails with an error. If optional promote goal fail, the plugins succeeds, but it issues a warning.)
 *
 * Note that the deployment date of artifacts in the repository may be later that the release date in the changes.xml.
 */
@Mojo(name = "promote", requiresDependencyResolution = ResolutionScope.TEST, requiresDependencyCollection = ResolutionScope.TEST)
public class Promote extends ProjectBase {
    /**
     * Message for svn commit of the new tag.
     */
    @Parameter(property = "prerelease.createTagMessage", defaultValue =
            "Prerelease ${revision} promoted to release ${release}.")
    protected String createTagMessage;

    /**
     * Svn commit message when reverting the tag.
     */
    @Parameter(property = "prerelease.revertTagMessage", defaultValue =
            "Reverting tag for release ${release} because the deployment failed.")
    protected String revertTagMessage;

    /**
     * Message for svn commit to start new development iteration.
     */
    @Parameter(property = "prerelease.nextIterationMessage", defaultValue =
            "Prerelease ${revision} promoted to release ${release}, starting next development iteration.")
    protected String nextIterationMessage;

    public void doExecute(Archive archive) throws Exception {
        WorkingCopy workingCopy;
        long revision;
        Prerelease prerelease;

        workingCopy = workingCopy().check();
        revision = workingCopy.revision();
        setTarget(archive.target(revision));
        prerelease = target.loadOpt(storages());
        if (prerelease == null) {
            throw new MojoExecutionException("no prerelease for revision " + revision);
        }
        prerelease.promote(getLog(), propertyArgs(), createTagMessage, revertTagMessage, nextIterationMessage, maven(),
                allowSnapshots, allowPrereleaseSnapshots);
        workingCopy.update(getLog());
    }
}
