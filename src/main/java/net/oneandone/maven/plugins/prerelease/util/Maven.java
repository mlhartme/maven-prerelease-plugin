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

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;

import java.util.Map;
import java.util.Properties;

public final class Maven {
    /** @param userProperties properties specified by the user with -D command line args. */
    public static Launcher mvn(FileNode basedir, Properties userProperties) {
        Launcher mvn;

        mvn = new Launcher(basedir, "mvn", "-B", /* same as mvn release plugin: */ "-DperformRelease");
        for (Map.Entry<Object, Object> entry : userProperties.entrySet()) {
            mvn.arg("-D" + entry.getKey() + "=" + entry.getValue());
        }
        return mvn;
    }

    private Maven() {
    }
}
