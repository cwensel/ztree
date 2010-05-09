/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zookeeper.ztree;

import java.io.Closeable;

import org.apache.log4j.Logger;
import org.apache.zookeeper.ZooKeeper;

/**
 * A ZNode which also manages the connection to ZooKeeper.
 */
public class ZRoot extends ZNode implements Closeable {
    private static final Logger logger = Logger.getLogger(ZRoot.class);
    private volatile boolean isOpen;

    /**
     * Constructor. Ensures that the root node exists.
     * 
     * @param zk
     *            the ZooKeeper client.
     */
    public ZRoot(ZooKeeper zk) {
        super(zk, PATH_SEPARATOR/* root */);
        isOpen = true;
        ensureRootNode();
    }

    /**
     * Makes sure that the root node exists. This allows clients the convenience
     * of automatically having a root node. If there is already one, this will
     * just back off silently.
     */
    private void ensureRootNode() {
        getOrCreatePath(PATH_SEPARATOR);
    }

    /**
     * Closes the connection to Zookeeper. No more zookeeping for you!
     */
    public void close() {
        if (!isOpen) {
            throw new IllegalStateException("ZRoot is already closed.");
        }
        try {
            zooKeeper.close();
            isOpen = false;
        } catch (InterruptedException e) {
            logger.error("Failed to close Zookeeper", e);
        }
    }

}
