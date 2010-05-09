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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.zookeeper.test.ClientBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Illustrative tests for the ZRoot and ZNode ZooKeeper wrapper interface.
 */
public class ZRootAndZNodeApiTest extends ClientBase {
    private final String NODE_NAME = "node_" + System.nanoTime();
    private ZRoot ZROOT;

    @Before
    public void setUpClient() throws Exception {
        super.setUp();
        ZROOT = new ZRoot(createClient());
    }

    @After
    public void tearDown() throws Exception {
        ZROOT.deleteChild(NODE_NAME);
        ZROOT.close();
    }

    @Test
    public void testExists_true() {
        ZNode node = ZROOT.createChild("aChild", "myData");
        assertTrue(node.exists());
    }

    @Test
    public void testExists_false() {
        ZNode node = ZROOT.getChild("UNICORN");
        assertFalse(node.exists());
    }

    @Test
    public void testCreateChild_WithData_DidNotAlreadyExist() {
        ZNode node = ZROOT.createChild(NODE_NAME, "myData");

        assertExists(node);
        assertEquals("myData", node.getData());
    }

    public void testGetOrCreateChild_EmptyData_DidNotAlreadyExist() {
        ZNode node = ZROOT.getOrCreateChild(NODE_NAME);

        assertExists(node);
        assertEquals("", node.getData());
    }

    @Test
    public void testGetOrCreateChild_EmptyData_AlreadyExists() {
        // setup
        ZROOT.getOrCreateChild(NODE_NAME);

        // effectively a NOOP; just verifying no Runtime Exception
        ZROOT.getOrCreateChild(NODE_NAME);
    }

    @Test
    public void testCreateChild_WithData_AlreadyExists() {
        // setup
        ZNode firstNode = ZROOT.createChild(NODE_NAME, "firstData");

        try {
            // try to create the same node with different data
            ZROOT.createChild(NODE_NAME, "rejectedData");
            fail("Expected a specific Exception.");
        } catch (NodeExistsException e) {
            // got exception and original data remains
            assertEquals("firstData", firstNode.getData());
        }
    }

    @Test
    public void testCreateOrUpdateChild_DoesNotAlreadyExist() {
        ZNode newNode = ZROOT.createOrUpdateChild(NODE_NAME, "newData");

        assertEquals("newData", newNode.getData());
    }

    @Test
    public void testCreateOrUpdateChild_AlreadyExists() {
        ZROOT.createChild(NODE_NAME, "oldData");
        ZNode second = ZROOT.createOrUpdateChild(NODE_NAME, "newData");

        assertEquals("newData", second.getData());
    }

    @Test
    public void testGetName() {
        ZNode node = ZROOT.getOrCreateChild(NODE_NAME);
        assertEquals(NODE_NAME, node.getName());
    }

    @Test
    public void testSetData() {
        ZNode node = ZROOT.getOrCreateChild(NODE_NAME);
        node.setData("myData");

        assertEquals("myData", node.getData());
    }

    @Test
    public void testDeleteChild_BaseCase() {
        ZNode node = ZROOT.getOrCreateChild(NODE_NAME);
        assertExists(node);
        ZROOT.deleteChild(NODE_NAME);

        assertDoesNotExist(node);
    }

    /**
     * Asking to delete a child that doesn't exist should just be a quiet NOOP.
     */
    @Test
    public void testDeleteChild_QuietWhenDoesNotExist() {
        ZROOT.deleteChild("UNICORN");
        assertDoesNotExist(ZROOT.getChild("UNICORN"));
    }

    @Test
    public void testDelete_NoChildren() {
        ZNode node = ZROOT.getOrCreateChild(NODE_NAME);
        node.delete();

        assertDoesNotExist(node);
    }

    @Test
    public void testDelete_WithChildren() {
        ZNode node = nodeWithThreeChildren();
        node.delete();

        assertDoesNotExist(node);
    }

    /**
     * Asking to delete a Node that doesn't exist should just be a quiet NOOP.
     */
    @Test
    public void testDelete_QuietWhenNoNode() {
        ZNode node = nonexistantNode();
        assertDoesNotExist(node);
        node.delete();
        assertDoesNotExist(node);
    }

    @Test
    public void testGetOrCreateChild_ChildDoesNotExist() {
        ZNode node = ZROOT.getOrCreateChild(NODE_NAME);
        ZNode child = node.getChild("aChild");

        assertPath(ZRoot.PATH_SEPARATOR + NODE_NAME + "/aChild", child);
        assertDoesNotExist(child);
    }

    @Test
    public void testGetOrCreateChild_ChildExists() {
        ZNode node = ZROOT.getOrCreateChild(NODE_NAME);
        node.getOrCreateChild("aChild");
        ZNode child = node.getChild("aChild");

        assertPath(ZRoot.PATH_SEPARATOR + NODE_NAME + "/aChild", child);
        assertExists(child);
    }

    @Test(expected = NoNodeException.class)
    public void testGetChild_setData_nodeDoesNotExist() {
        ZROOT.getChild(NODE_NAME).setData("bad_data");
    }

    @Test(expected = NoNodeException.class)
    public void testGetChild_getData_nodeDoesNotExist() {
        ZROOT.getChild(NODE_NAME).getData();
    }

    @Test
    public void testGetChildren() {
        ZNode node = nodeWithThreeChildren();
        Set<String> names = namesOfChildren(node);
        assertEquals(3, names.size());
        assertTrue(names.contains("aChild1"));
        assertTrue(names.contains("aChild2"));
        assertTrue(names.contains("aChild3"));
    }

    @Test
    public void testGetChildren_Empty() {
        ZNode node = ZROOT.getOrCreateChild(NODE_NAME);
        assertEquals(0, node.getChildren().size());
    }

    @Test
    public void testGetChildrenIntoHash() {
        ZNode node = nodeWithThreeChildren();
        Map<String, ZNode> hash = node.getChildrenIntoHash();

        assertEquals(3, hash.size());
        assertEquals("aChild1", hash.get("aChild1").getName());
        assertEquals("aChild2", hash.get("aChild2").getName());
        assertEquals("aChild3", hash.get("aChild3").getName());
    }

    @Test
    public void testGetOrCreateChildrenIntoHash() {
        ZNode node = ZROOT.getOrCreateChild(NODE_NAME);
        Map<String, ZNode> hash = node.getOrCreateChildrenIntoHash("aChild1",
                "aChild2", "aChild3");

        assertEquals(3, hash.size());
        assertEquals("aChild1", hash.get("aChild1").getName());
        assertEquals("aChild2", hash.get("aChild2").getName());
        assertEquals("aChild3", hash.get("aChild3").getName());
    }

    @Test
    public void testGetOrCreateBranch_3Descendants() {
        ZNode node = ZROOT.getOrCreateChild(NODE_NAME);
        ZNode greatGrandChild = node.getOrCreateBranch("child", "grand-child",
                "great-grand-child");

        assertTrue(node.getChild("child").exists());
        assertTrue(node.getChild("child").getChild("grand-child").exists());
        assertTrue(greatGrandChild.exists());
    }

    @Test
    public void testGetOrCreateBranch_1Descendant() {
        ZNode node = ZROOT.getOrCreateChild(NODE_NAME);
        ZNode child = node.getOrCreateBranch("child");
        assertTrue(child.exists());
    }

    private Set<String> namesOfChildren(ZNode node) {
        Collection<ZNode> children = node.getChildren();
        assertEquals(3, children.size());
        Set<String> names = new HashSet<String>();
        for (ZNode child : children) {
            names.add(child.getName());
        }
        return names;
    }

    private ZNode nodeWithThreeChildren() {
        ZNode node = ZROOT.getOrCreateChild(NODE_NAME);
        node.getOrCreateChild("aChild1");
        node.getOrCreateChild("aChild2");
        node.getOrCreateChild("aChild3");
        return node;
    }

    private ZNode nonexistantNode() {
        return new ZNode(ZROOT.zooKeeper, "/UNICORN");
    }

    private void assertExists(ZNode node) {
        assertTrue(node.exists());
    }

    private void assertDoesNotExist(ZNode child) {
        assertFalse(child.exists());
    }

    private void assertPath(String expectedPath, ZNode child) {
        assertEquals(expectedPath, child.getPath());
    }

}