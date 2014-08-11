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

import net.oneandone.maven.plugins.prerelease.util.IntegrationBase;
import net.oneandone.sushi.fs.MkfileException;
import org.apache.maven.plugin.logging.Log;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.fail;

public class ArchiveIT extends IntegrationBase {
    @Test
    public void recoverFromOOM() throws Exception {
        Storages storages;
        long[] toomuch;
        Project project;

        storages = testStorages();
        project = testProject();
        try{
            try (Archive archive = storages.openOne(project, 1, nullLog())) {
                toomuch = new long[Integer.MAX_VALUE];
                toomuch[0] = 0;
            }
            fail();
        } catch (OutOfMemoryError e) {
            // ok
        }
        // make sure the lock was removed
        storages.openOne(project, 1, nullLog()).close();
    }

    @Test
    public void lock2Thread() throws Exception {
        final Storages storages;
        final Project project;
        final Archive archive;
        final Archive archive2;

        storages = testStorages();
        project = testProject();
        archive = storages.openOne(project, 5, nullLog());
        new Thread() {
            public void run() {
                try {
                    Thread.sleep(5);
                    archive.close();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }.start();
        archive2 = storages.openOne(project, 10, systemOutLog());
        archive2.close();
    }

    @Test
    public void lockTimeout() throws Exception {
        final Storages storages;
        final Project project;
        final Archive archive;

        storages = testStorages();
        project = testProject();
        archive = storages.openOne(project, 5, nullLog());
        try {
            try (Archive archive2 = storages.openOne(project, 1, nullLog())) {
                // empty - exception expected
            }
            fail();
        } catch (MkfileException e) {
            // ok
        }
        archive.close();
    }

    public static Log nullLog() {
        return new Log() {
            @Override
            public boolean isDebugEnabled() {
                return false;
            }

            @Override
            public void debug(CharSequence charSequence) {
            }

            @Override
            public void debug(CharSequence charSequence, Throwable throwable) {
            }

            @Override
            public void debug(Throwable throwable) {
            }

            @Override
            public boolean isInfoEnabled() {
                return false;
            }

            @Override
            public void info(CharSequence charSequence) {
            }

            @Override
            public void info(CharSequence charSequence, Throwable throwable) {
            }

            @Override
            public void info(Throwable throwable) {
            }

            @Override
            public boolean isWarnEnabled() {
                return false;
            }

            @Override
            public void warn(CharSequence charSequence) {
            }

            @Override
            public void warn(CharSequence charSequence, Throwable throwable) {
            }

            @Override
            public void warn(Throwable throwable) {
            }

            @Override
            public boolean isErrorEnabled() {
                return false;
            }

            @Override
            public void error(CharSequence charSequence) {
            }

            @Override
            public void error(CharSequence charSequence, Throwable throwable) {
            }

            @Override
            public void error(Throwable throwable) {
            }
        };
    }
    public static Log systemOutLog() {
        return new Log() {
            @Override
            public boolean isDebugEnabled() {
                return false;
            }

            @Override
            public void debug(CharSequence charSequence) {
            }

            @Override
            public void debug(CharSequence charSequence, Throwable throwable) {
            }

            @Override
            public void debug(Throwable throwable) {
            }

            @Override
            public boolean isInfoEnabled() {
                return false;
            }

            @Override
            public void info(CharSequence charSequence) {
                System.out.println(charSequence);
            }

            @Override
            public void info(CharSequence charSequence, Throwable throwable) {
                System.out.println(charSequence);
                throwable.printStackTrace(System.out);
            }

            @Override
            public void info(Throwable throwable) {
            }

            @Override
            public boolean isWarnEnabled() {
                return false;
            }

            @Override
            public void warn(CharSequence charSequence) {
            }

            @Override
            public void warn(CharSequence charSequence, Throwable throwable) {
            }

            @Override
            public void warn(Throwable throwable) {
            }

            @Override
            public boolean isErrorEnabled() {
                return false;
            }

            @Override
            public void error(CharSequence charSequence) {
            }

            @Override
            public void error(CharSequence charSequence, Throwable throwable) {
            }

            @Override
            public void error(Throwable throwable) {
            }
        };
    }

    private Storages testStorages() throws IOException {
        return new Storages(Collections.singletonList(WORLD.getTemp().createTempDirectory()));
    }

    private Project testProject() {
        return new Project("foo", "bar", "1-SNAPSHOT");
    }
}
