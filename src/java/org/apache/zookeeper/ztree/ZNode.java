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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;

/**
 * A node interface for interacting with ZooKeeper. This is similar to Java's
 * File interface, with some added conveniences such as being able to cleanly
 * delete an entire branch.
 * <p>
 * This design attempts to offer a high level of convenience and fluidity. As an
 * example, consider the following chained operation: <br>
 * 
 * <pre>
 * aNode.getChild(&quot;aChild1&quot;).getChild(&quot;aChild2&quot;).deleteChild(&quot;aChild3&quot;);
 * </pre>
 * 
 * In the above example, it is OK if none of the specified nodes exist. In that
 * case, the calls will return without error; the node to be deleted didn't
 * exist so everything is as it should be.
 * <p>
 * Exception handling: ZooKeeper checked exceptions are caught and re-thrown as
 * general unchecked exceptions. In some situations, specific exceptions are
 * thrown to indicate certain kinds of unsupported operations, such as a
 * {@link NoNodeException} to indicate that an operation was illegally attempted
 * on a node that doesn't exist.
 */
public class ZNode {
    protected final ZooKeeper zooKeeper;
    protected static final String PATH_SEPARATOR = "/";
    protected static final byte[] EMPTY_DATA = new byte[0];
    private static final int ANY_VERSION = -1;
    private static final List<ACL> ACL = Ids.OPEN_ACL_UNSAFE;
    private static final CreateMode MODE = CreateMode.PERSISTENT;
    private final String path;

    /**
     * Constructor. Protected so that API users must go through a {@link ZTree}
     * instance to obtain nodes.
     * 
     * @param zooKeeper
     *            a valid QuartzZooKeeper reference.
     * @param path
     *            the path of this node.
     */
    protected ZNode(ZooKeeper zooKeeper, String path) {
        final String ahad = "Hi!";
        if (ahad == null) {
            System.out.println("EH!?");
        }
        this.zooKeeper = zooKeeper;
        this.path = path;
    }

    /**
     * @return true if this node exists in the underlying Zookeeper tree.
     */
    public boolean exists() {
        try {
            return zooKeeper.exists(path, false) != null;
        } catch (KeeperException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Updates the data associated with this node.
     * 
     * @param data
     *            the new data to set for this node.
     * @throws NoNodeException
     *             if this node does not exist in the backing ZooKeeper tree
     */
    public void setData(String data) {
        try {
            zooKeeper.setData(path, data.getBytes(), ANY_VERSION);
        } catch (KeeperException e) {
            if (isNoNodeException(e)) {
                throw new NoNodeException(e);
            } else {
                throw new RuntimeException(e);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the data associated with this node.
     * @throws NoNodeException
     *             if this node does not exist in the backing ZooKeeper tree
     */
    public String getData() {
        try {
            return getData(path);
        } catch (KeeperException e) {
            if (isNoNodeException(e)) {
                throw new NoNodeException(e);
            } else {
                throw new RuntimeException(e);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the name of this node.
     */
    public String getName() {
        if (isRoot(path)) {
            return PATH_SEPARATOR;
        } else {
            return path.substring(path.lastIndexOf(PATH_SEPARATOR) + 1);
        }
    }

    /**
     * Obtains the leaf node of an arbitrarily long branch of nodes, starting at
     * this node. Creates all nodes in the branch as necessary.
     * 
     * @param orderedDescendantNames
     *            the ordered names of descendant nodes in the branch, starting
     *            with the immediate child of this node.
     * @return the leaf node in the specified branch.
     */
    public ZNode getOrCreateBranch(String... orderedDescendantNames) {
        ZNode descendant = getOrCreateChild(orderedDescendantNames[0]);
        for (int i = 1; i < orderedDescendantNames.length; i++) {
            descendant = descendant.getOrCreateChild(orderedDescendantNames[i]);
        }
        return descendant;
    }

    /**
     * @param childNames
     *            the names of children to get or create.
     * @return a hash with the children name by <tt>childNames</tt>, creating
     *         them if they don't exist.
     */
    public Map<String, ZNode> getOrCreateChildrenIntoHash(String... childNames) {
        Map<String, ZNode> hash = new HashMap<String, ZNode>();
        for (String childName : childNames) {
            hash.put(childName, getOrCreateChild(childName));
        }
        return hash;
    }

    /**
     * @return a hash with keys for child names, values of ZNodes.
     */
    public Map<String, ZNode> getChildrenIntoHash() {
        Map<String, ZNode> hash = new HashMap<String, ZNode>();
        for (ZNode child : getChildren()) {
            hash.put(child.getName(), child);
        }
        return hash;
    }

    /**
     * @return the Children associated with this node.
     */
    public Collection<ZNode> getChildren() {
        try {
            return getChildrenImpl(path);
        } catch (KeeperException e) {
            return emptyChildrenOnNonodeOrRethrow(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deletes this node's children recursively, but leaves the node in place,
     * with current data.
     */
    public void deleteChildren() {
        deleteChildren(this);
    }

    /**
     * Deletes the specified child and all its children. If the node does not
     * exist, this is a quiet NOOP.
     */
    public void deleteChild(String childName) {
        getChild(childName).delete();
    }

    /**
     * Deletes the Node with the specified path, recursively.
     * <p>
     * If the Node has children, all the children will be deleted as well (and
     * all the children's children, etc.).
     * <p>
     * If the Node already does not exist, this is a quiet NOOP.
     * 
     * @param path
     *            the path of the Node to delete.
     */
    public void delete() {
        try {
            zooKeeper.delete(path, ANY_VERSION);
        } catch (KeeperException e) {
            handleKeeperExceptionOnDelete(path, e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Collection<ZNode> getChildrenImpl(String path)
            throws KeeperException, InterruptedException {
        Collection<ZNode> children = new HashSet<ZNode>();
        for (String childPath : zooKeeper.getChildren(path, false)) {
            children.add(new ZNode(zooKeeper, pathForChild(path, childPath)));
        }
        return children;
    }

    private String pathForChild(String parentPath, String childName) {
        if (isRoot(parentPath)) {
            // Special Case: from root node, avoid "//"
            return parentPath + childName;
        } else {
            return parentPath + PATH_SEPARATOR + childName;
        }
    }

    private Collection<ZNode> emptyChildrenOnNonodeOrRethrow(KeeperException e) {
        if (isNoNodeException(e)) {
            // a NONODE certainly can't have children
            return Collections.emptySet();
        } else {
            throw new RuntimeException(e);
        }
    }

    /**
     * Obtains the child of this node with the specified name, creating it if it
     * doesn't already exist.
     * 
     * @param childName
     *            the name of the desired child node.
     * @return the specified child node.
     */
    public ZNode getOrCreateChild(String childName) {
        return getOrCreatePath(pathForChild(childName));
    }

    /**
     * Obtains the child node with the specified name, or an empty
     * representation if it doesn't already exist.
     * <p>
     * This method will not create the specified child if it doesn't already
     * exist. The empty representation returned in that case will not support
     * certain operations, such as getting or setting data. Trying to get or set
     * data on an empty representation will result in a NoNodeException.
     * 
     * @param childName
     *            the name of the desired child node.
     * @return the node that represents the specified child of this node (by
     *         name). Will be an emtpy representation if the specified child
     *         node does not exist.
     */
    public ZNode getChild(String childName) {
        return new ZNode(zooKeeper, pathForChild(childName));
    }

    /**
     * Ensures that a child node exists with the specified name and data.
     * 
     * @param childName
     *            the name of the child node to ensure.
     * @param data
     *            the data to ensure is set for the child node.
     * @return the specified child node.
     */
    public ZNode createOrUpdateChild(String childName, String data) {
        /**
         * Optimism: we expect the node to not yet exist, more frequently than
         * it already exists.
         */
        try {
            return createChild(childName, data);
        } catch (NodeExistsException e) {
            ZNode child = getChild(childName);
            child.setData(data);
            return child;
        }
    }

    /**
     * Creates a child of this node, with the specified name and data. There
     * must not already be a child of this node with that name.
     * 
     * @param childName
     *            the name of the new child node.
     * @param data
     *            the data of the new child node.
     * @return the newly created child node.
     * @throws NodeExistsException
     *             if the specified node (by name) already exists.
     */
    public ZNode createChild(String childName, String data) {
        return createNode(pathForChild(childName), data);
    }

    /**
     * Creates a node at the specified path, with the specified data. The node
     * must not already exist.
     * 
     * @param path
     *            the path to the desired node. "/a/path/to/mynode"
     * @param data
     *            the desired data.
     * @return the desired node.
     * @throws NodeExistsException
     *             if the specified node (by name) already exists.
     */
    private ZNode createNode(String path, String data) {
        return createPath(path, data.getBytes());
    }

    private ZNode createPath(String path, byte[] data) {
        try {
            zooKeeper.create(path, data, ACL, MODE);
            return new ZNode(zooKeeper, path);
        } catch (KeeperException e) {
            if (isNodeExistsException(e)) {
                throw new NodeExistsException(e);
            } else {
                throw new RuntimeException(e);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Obtains the node at the specified path, creating it if it doesn't already
     * exist.
     * 
     * @param path
     *            the path to the desired node. Example: "/a/path/to/mynode"
     * @return the desired node.
     */
    protected ZNode getOrCreatePath(String path) {
        ZNode node = new ZNode(zooKeeper, path);
        if (!node.exists()) {
            createQuietly(path);
        }
        return node;
    }

    /**
     * Attempts to create the specified path. Quietly swallows a Node Exists
     * Exception.
     */
    private void createQuietly(String path) {
        try {
            zooKeeper.create(path, EMPTY_DATA, ACL, MODE);
        } catch (KeeperException e) {
            if (!(e.code() == Code.NODEEXISTS)) {
                throw new RuntimeException(e);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return path;
    }

    protected String getPath() {
        return path;
    }

    private final String pathForChild(String childName) {
        return pathForChild(path, childName);
    }

    private String getData(String path) throws KeeperException,
            InterruptedException {
        Stat nodeStatus = zooKeeper.exists(path, false);
        if (null == nodeStatus) {
            throw new NoNodeException("Node does not exist: " + path);
        } else {
            return new String(zooKeeper.getData(path, false, nodeStatus));
        }
    }

    private void handleKeeperExceptionOnDelete(String path, KeeperException e) {
        if (e.code() == Code.NOTEMPTY) {
            // we optimistically tried a simple delete of the path,
            // but there were children. so now let's do a full
            // (pessimistic) recursive delete.
            deleteRecursively(new ZNode(zooKeeper, path));
            return;
        } else if (isNoNodeException(e)) {
            // a NONODE on a delete really means there's no work to do
            return;
        } else {
            throw new RuntimeException(e);
        }
    }

    private boolean isNoNodeException(KeeperException e) {
        return e.code() == Code.NONODE;
    }

    private boolean isNodeExistsException(KeeperException e) {
        return e.code() == Code.NODEEXISTS;
    }

    private void deleteRecursively(ZNode node) {
        deleteChildren(node);
        node.delete();
    }

    private void deleteChildren(ZNode node) {
        for (ZNode child : node.getChildren()) {
            deleteRecursively(child);
        }
    }

    /**
     * @return true if the specified path is the root of this Zookeeper scheme.
     */
    private static final boolean isRoot(String path) {
        return PATH_SEPARATOR.equals(path);
    }

    /**
     * Convenience method to return a simple textual listing of the tree from
     * this node.
     */
    public String getListing() {
        StringBuilder listing = new StringBuilder();
        addListing(listing, this, 0);
        return listing.toString();
    }

    private void addListing(StringBuilder listing, ZNode node, int tabLevel) {
        listing.append(tabs(tabLevel)).append(node.getName());
        appendDataIfExists(listing, node);
        listing.append("\n");
        for (ZNode child : node.getChildren()) {
            addListing(listing, child, tabLevel + 1);
        }
    }

    private void appendDataIfExists(StringBuilder listing, ZNode node) {
        String data = node.getData();
        if (data != null && data.length() > 0) {
            listing.append(" [").append(node.getData()).append("]");
        }
    }

    private static StringBuilder tabs(int level) {
        StringBuilder sbul = new StringBuilder();
        for (int i = 0; i < level; i++)
            sbul.append("  ");
        return sbul;
    }

}
