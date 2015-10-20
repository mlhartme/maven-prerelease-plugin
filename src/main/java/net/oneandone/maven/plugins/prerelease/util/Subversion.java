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
package net.oneandone.maven.plugins.prerelease.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.logging.Log;

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;

public final class Subversion {
    public static Launcher launcher(FileNode dir, SvnCredentials credentials,  String ... args) {

        Launcher launcher = new Launcher(dir, "svn", "--non-interactive", "--no-auth-cache");
        if (credentials != null && credentials.username() != null) {
            launcher = launcher.arg("--username", credentials.username());
        }
        if (credentials != null && credentials.password() != null) {
            launcher = launcher.arg("--password", credentials.password());
        }
        return launcher.arg(args);
    }

    public static boolean exists(FileNode dir, SvnCredentials credentials, String svnurl) {
        try {
            Subversion.launcher(dir, credentials, "ls", svnurl).exec();
            return true;
        } catch (Failure e) {
            return false;
        }
    }

    public static void sparseCheckout(Log log, FileNode result, String svnurl, String revision, boolean tryChanges, SvnCredentials credentials) throws Failure {
        log.debug(Subversion.launcher(result.getParent(), credentials, "co", "-r", revision, "--depth", "empty", svnurl, result.getName()).exec());
        log.debug(Subversion.launcher(result, credentials, "up", "-r", revision, "pom.xml").exec());
        if (tryChanges) {
            log.debug(Subversion.launcher(result, credentials, "up", "-r", revision, "--depth", "empty", "src/changes").exec());
            log.debug(Subversion.launcher(result, credentials, "up", "-r", revision, "--depth", "empty", "src").exec());
            log.debug(Subversion.launcher(result, credentials, "up", "-r", revision, "src/changes/changes.xml").exec());
        }
    }

    private Subversion() {
    }

    //--

    private static final Pattern PATTERN = Pattern.compile("^URL:(.*)$", Pattern.CASE_INSENSITIVE| Pattern.MULTILINE);

    public static String workspaceUrl(FileNode directory, SvnCredentials credentials) throws Failure {
        String str;
        Matcher matcher;

        str = Subversion.launcher(directory, credentials, "info").exec();
        matcher = PATTERN.matcher(str);
        if (!matcher.find()) {
            throw new IllegalStateException("cannot determine checkout url in " + str);
        }
        return matcher.group(1).trim();
    }

    public static class SvnCredentials{
        public static final SvnCredentials NONE() {
            return new SvnCredentials(null, null);
        }
        private final String username;
        private final String password;
        public SvnCredentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
        private String username() {
            return username;
        }
        private String password() {
            return password;
        }
    }
}
