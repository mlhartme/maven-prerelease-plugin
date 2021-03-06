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

import net.oneandone.maven.plugins.prerelease.util.IntegrationBase;
import net.oneandone.sushi.fs.file.FileNode;
import org.junit.Test;

public class MojoIT extends IntegrationBase {
    private final String PREFIX = "net.oneandone.maven.plugins:prerelease:" + System.getProperty("prerelease.version") + ":";

    @Test
    public void minimal() throws Exception {
        FileNode checkout;

        checkout = checkoutProject("minimal");
        mvn(checkout, PREFIX + "list");
        mvn(checkout, PREFIX + "create");
        mvn(checkout, PREFIX + "update");
        mvn(checkout, PREFIX + "update");
        mvn(checkout, PREFIX + "promote");
    }
}
