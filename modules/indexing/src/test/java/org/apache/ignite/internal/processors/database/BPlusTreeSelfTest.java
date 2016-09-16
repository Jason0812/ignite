/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.database;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLongArray;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider;
import org.apache.ignite.internal.pagemem.FullPageId;
import org.apache.ignite.internal.pagemem.PageIdAllocator;
import org.apache.ignite.internal.pagemem.PageMemory;
import org.apache.ignite.internal.pagemem.impl.PageMemoryNoStoreImpl;
import org.apache.ignite.internal.processors.cache.database.tree.BPlusTree;
import org.apache.ignite.internal.processors.cache.database.tree.io.BPlusIO;
import org.apache.ignite.internal.processors.cache.database.tree.io.BPlusInnerIO;
import org.apache.ignite.internal.processors.cache.database.tree.io.BPlusLeafIO;
import org.apache.ignite.internal.processors.cache.database.tree.io.IOVersions;
import org.apache.ignite.internal.processors.cache.database.tree.io.PageIO;
import org.apache.ignite.internal.processors.cache.database.tree.reuse.ReuseList;
import org.apache.ignite.internal.util.GridConcurrentHashSet;
import org.apache.ignite.internal.util.GridRandom;
import org.apache.ignite.internal.util.lang.GridCursor;
import org.apache.ignite.internal.util.typedef.X;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.testframework.GridTestUtils;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;

import static org.apache.ignite.internal.processors.cache.database.tree.BPlusTree.rnd;

/**
 */
public class BPlusTreeSelfTest extends GridCommonAbstractTest {
    /** */
    private static final short LONG_INNER_IO = 30000;

    /** */
    private static final short LONG_LEAF_IO = 30001;

    /** */
    protected static final int PAGE_SIZE = 256;

    /** */
    protected static final long MB = 1024 * 1024;

    /** */
    protected static final int CPUS = Runtime.getRuntime().availableProcessors();

    /** */
    private static final int CACHE_ID = 100500;

    /** */
    protected static int MAX_PER_PAGE = 0;

    /** */
    protected static int CNT = 10;

    /** */
    private static int PUT_INC = 1;

    /** */
    private static int RMV_INC = 1;

    /** */
    protected PageMemory pageMem;

    /** */
    private ReuseList reuseList;

    /** */
    private static final Collection<Long> rmvdIds = new GridConcurrentHashSet<>();


//    /** {@inheritDoc} */
//    @Override protected long getTestTimeout() {
//        return 25 * 60 * 1000;
//    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        long seed = System.nanoTime();

        X.println("Test seed: " + seed + "L; // ");

        rnd = new Random(seed);

        pageMem = createPageMemory();

        reuseList = createReuseList(CACHE_ID, pageMem, 0, true);
    }

    /**
     * @param cacheId Cache ID.
     * @param pageMem Page memory.
     * @param rootId Root page ID.
     * @param initNew Init new flag.
     * @return Reuse list.
     * @throws IgniteCheckedException If failed.
     */
    protected ReuseList createReuseList(int cacheId, PageMemory pageMem, long rootId, boolean initNew)
        throws IgniteCheckedException {
        return null;
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        rnd = null;

        if (reuseList != null) {
            long size = reuseList.recycledPagesCount();

            assertTrue("Reuse size: " + size, size < 6000);
        }

        for (int i = 0; i < 10; i++) {
            if (acquiredPages() != 0) {
                System.out.println("!!!");
                U.sleep(10);
            }
        }

        assertEquals(0, acquiredPages());

        pageMem.stop();

        MAX_PER_PAGE = 0;
        PUT_INC = 1;
        RMV_INC = -1;
        CNT = 10;
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_1_20_mm_1() throws IgniteCheckedException {
        MAX_PER_PAGE = 1;
        CNT = 20;
        PUT_INC = -1;
        RMV_INC = -1;

        doTestPutRemove(true);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_1_20_mm_0() throws IgniteCheckedException {
        MAX_PER_PAGE = 1;
        CNT = 20;
        PUT_INC = -1;
        RMV_INC = -1;

        doTestPutRemove(false);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_1_20_pm_1() throws IgniteCheckedException {
        MAX_PER_PAGE = 1;
        CNT = 20;
        PUT_INC = 1;
        RMV_INC = -1;

        doTestPutRemove(true);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_1_20_pm_0() throws IgniteCheckedException {
        MAX_PER_PAGE = 1;
        CNT = 20;
        PUT_INC = 1;
        RMV_INC = -1;

        doTestPutRemove(false);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_1_20_pp_1() throws IgniteCheckedException {
        MAX_PER_PAGE = 1;
        CNT = 20;
        PUT_INC = 1;
        RMV_INC = 1;

        doTestPutRemove(true);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_1_20_pp_0() throws IgniteCheckedException {
        MAX_PER_PAGE = 1;
        CNT = 20;
        PUT_INC = 1;
        RMV_INC = 1;

        doTestPutRemove(false);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_1_20_mp_1() throws IgniteCheckedException {
        MAX_PER_PAGE = 1;
        CNT = 20;
        PUT_INC = -1;
        RMV_INC = 1;

        doTestPutRemove(true);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_1_20_mp_0() throws IgniteCheckedException {
        MAX_PER_PAGE = 1;
        CNT = 20;
        PUT_INC = -1;
        RMV_INC = 1;

        doTestPutRemove(false);
    }

    // ------- 2 - 40
    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_2_40_mm_1() throws IgniteCheckedException {
        MAX_PER_PAGE = 2;
        CNT = 40;
        PUT_INC = -1;
        RMV_INC = -1;

        doTestPutRemove(true);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_2_40_mm_0() throws IgniteCheckedException {
        MAX_PER_PAGE = 2;
        CNT = 40;
        PUT_INC = -1;
        RMV_INC = -1;

        doTestPutRemove(false);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_2_40_pm_1() throws IgniteCheckedException {
        MAX_PER_PAGE = 2;
        CNT = 40;
        PUT_INC = 1;
        RMV_INC = -1;

        doTestPutRemove(true);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_2_40_pm_0() throws IgniteCheckedException {
        MAX_PER_PAGE = 2;
        CNT = 40;
        PUT_INC = 1;
        RMV_INC = -1;

        doTestPutRemove(false);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_2_40_pp_1() throws IgniteCheckedException {
        MAX_PER_PAGE = 2;
        CNT = 40;
        PUT_INC = 1;
        RMV_INC = 1;

        doTestPutRemove(true);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_2_40_pp_0() throws IgniteCheckedException {
        MAX_PER_PAGE = 2;
        CNT = 40;
        PUT_INC = 1;
        RMV_INC = 1;

        doTestPutRemove(false);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_2_40_mp_1() throws IgniteCheckedException {
        MAX_PER_PAGE = 2;
        CNT = 40;
        PUT_INC = -1;
        RMV_INC = 1;

        doTestPutRemove(true);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_2_40_mp_0() throws IgniteCheckedException {
        MAX_PER_PAGE = 2;
        CNT = 40;
        PUT_INC = -1;
        RMV_INC = 1;

        doTestPutRemove(false);
    }

    // ------- 3 - 60
    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_3_60_mm_1() throws IgniteCheckedException {
        MAX_PER_PAGE = 3;
        CNT = 60;
        PUT_INC = -1;
        RMV_INC = -1;

        doTestPutRemove(true);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_3_60_mm_0() throws IgniteCheckedException {
        MAX_PER_PAGE = 3;
        CNT = 60;
        PUT_INC = -1;
        RMV_INC = -1;

        doTestPutRemove(false);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_3_60_pm_1() throws IgniteCheckedException {
        MAX_PER_PAGE = 3;
        CNT = 60;
        PUT_INC = 1;
        RMV_INC = -1;

        doTestPutRemove(true);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_3_60_pm_0() throws IgniteCheckedException {
        MAX_PER_PAGE = 3;
        CNT = 60;
        PUT_INC = 1;
        RMV_INC = -1;

        doTestPutRemove(false);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_3_60_pp_1() throws IgniteCheckedException {
        MAX_PER_PAGE = 3;
        CNT = 60;
        PUT_INC = 1;
        RMV_INC = 1;

        doTestPutRemove(true);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_3_60_pp_0() throws IgniteCheckedException {
        MAX_PER_PAGE = 3;
        CNT = 60;
        PUT_INC = 1;
        RMV_INC = 1;

        doTestPutRemove(false);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_3_60_mp_1() throws IgniteCheckedException {
        MAX_PER_PAGE = 3;
        CNT = 60;
        PUT_INC = -1;
        RMV_INC = 1;

        doTestPutRemove(true);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testPutRemove_3_60_mp_0() throws IgniteCheckedException {
        MAX_PER_PAGE = 3;
        CNT = 60;
        PUT_INC = -1;
        RMV_INC = 1;

        doTestPutRemove(false);
    }

    /**
     * @param canGetRow Can get row from inner page.
     * @throws IgniteCheckedException If failed.
     */
    private void doTestPutRemove(boolean canGetRow) throws IgniteCheckedException {
        TestTree tree = createTestTree(canGetRow);

        long cnt = CNT;

        for (long x = PUT_INC > 0 ? 0 : cnt - 1; x >= 0 && x < cnt; x += PUT_INC) {
            assertNull(tree.findOne(x));

            tree.put(x);

            assertEquals(x, tree.findOne(x).longValue());
        }

        X.println(tree.printTree());

        assertNull(tree.findOne(-1L));

        for (long x = 0; x < cnt; x++)
            assertEquals(x, tree.findOne(x).longValue());

        assertNull(tree.findOne(cnt));

        for (long x = RMV_INC > 0 ? 0 : cnt - 1; x >= 0 && x < cnt; x += RMV_INC) {
            X.println(" -- " + x);

            assertEquals(x, tree.remove(x).longValue());

            X.println(tree.printTree());

            assertNull(tree.findOne(x));
        }

        assertFalse(tree.find(null, null).next());
        assertEquals(0, tree.size());
        assertEquals(0, tree.rootLevel());
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testRandomPutRemove_1_30_0() throws IgniteCheckedException {
        MAX_PER_PAGE = 1;
        CNT = 30;

        doTestRandomPutRemove(false);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testRandomPutRemove_1_30_1() throws IgniteCheckedException {
        MAX_PER_PAGE = 1;
        CNT = 30;

        doTestRandomPutRemove(true);
    }

    /**
     * @throws Exception If failed.
     */
    public void testMassiveRemove2_false() throws Exception {
        MAX_PER_PAGE = 2;

        doTestMassiveRemove(false);
    }

    /**
     * @throws Exception If failed.
     */
    public void testMassiveRemove2_true() throws Exception {
        MAX_PER_PAGE = 2;

        doTestMassiveRemove(true);
    }

    /**
     * @throws Exception If failed.
     */
    public void testMassiveRemove1_false() throws Exception {
        MAX_PER_PAGE = 1;

        doTestMassiveRemove(false);
    }

    /**
     * @throws Exception If failed.
     */
    public void testMassiveRemove1_true() throws Exception {
        MAX_PER_PAGE = 1;

        doTestMassiveRemove(true);
    }

    /**
     * @param canGetRow Can get row in inner page.
     * @throws Exception If failed.
     */
    private void doTestMassiveRemove(final boolean canGetRow) throws Exception {
        final int threads = 64;
        final int keys = 3000;

        final AtomicLongArray rmvd = new AtomicLongArray(keys);

        final TestTree tree = createTestTree(canGetRow);

        // Put keys in reverse order to have a better balance in the tree (lower height).
        for (long i = keys - 1; i >= 0; i--) {
            tree.put(i);
//            X.println(tree.printTree());
        }

        assertEquals(keys, tree.size());

        tree.validateTree();

        info("Remove...");

        try {
            GridTestUtils.runMultiThreaded(new Callable<Object>() {
                @Override public Object call() throws Exception {
                    Random rnd = new GridRandom();

                    for(;;) {
                        int idx = 0;
                        boolean found = false;

                        for (int i = 0, shift = rnd.nextInt(keys); i < keys; i++) {
                            idx = (i + shift) % keys;

                            if (rmvd.get(idx) == 0 && rmvd.compareAndSet(idx, 0, 1)) {
                                found = true;

                                break;
                            }
                        }

                        if (!found)
                            break;

                        assertEquals(Long.valueOf(idx), tree.remove((long)idx));

                        if (canGetRow)
                            rmvdIds.add((long)idx);
                    }

                    return null;
                }
            }, threads, "remove");

            assertEquals(0, tree.size());

            tree.validateTree();
        }
        finally {
            rmvdIds.clear();
        }
    }

    /**
     * @param canGetRow Can get row from inner page.
     * @throws IgniteCheckedException If failed.
     */
    private void doTestRandomPutRemove(boolean canGetRow) throws IgniteCheckedException {
        TestTree tree = createTestTree(canGetRow);

        Map<Long,Long> map = new HashMap<>();

        int loops = reuseList == null ? 300_000 : 1000_000;

        for (int i = 0 ; i < loops; i++) {
            Long x = (long)BPlusTree.randomInt(CNT);

            boolean put = BPlusTree.randomInt(2) == 0;

            if (i % 1000 == 0) {
//                X.println(tree.printTree());
                X.println(" --> " + (put ? "put " : "rmv ") + i + "  " + x);
            }

            if (put)
                assertEquals(map.put(x, x), tree.put(x));
            else {
                if (map.remove(x) != null)
                    assertEquals(x, tree.remove(x));

                assertNull(tree.remove(x));
            }

//            X.println(tree.printTree());
            tree.validateTree();

            if (i % 100 == 0) {
                GridCursor<Long> cursor = tree.find(null, null);

                while (cursor.next()) {
                    x = cursor.get();

                    assert x != null;

                    assertEquals(map.get(x), x);
                }

                assertEquals(map.size(), tree.size());
            }
        }
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testEmptyCursors() throws IgniteCheckedException {
        MAX_PER_PAGE = 5;

        TestTree tree = createTestTree(true);

        assertFalse(tree.find(null, null).next());
        assertFalse(tree.find(0L, 1L).next());

        tree.put(1L);
        tree.put(2L);
        tree.put(3L);

        assertEquals(3, size(tree.find(null, null)));

        assertFalse(tree.find(4L, null).next());
        assertFalse(tree.find(null, 0L).next());
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void testCursorConcurrentMerge() throws IgniteCheckedException {
        MAX_PER_PAGE = 5;

//        X.println(" " + pageMem.pageSize());

        TestTree tree = createTestTree(true);

        TreeMap<Long,Long> map = new TreeMap<>();

        for (int i = 0; i < 20_000 + rnd.nextInt(2 * MAX_PER_PAGE); i++) {
            Long row = (long)rnd.nextInt(40_000);

//            X.println(" <-- " + row);

            assertEquals(map.put(row, row), tree.put(row));
            assertEquals(row, tree.findOne(row));
        }

        final int off = rnd.nextInt(5 * MAX_PER_PAGE);

        Long upperBound = 30_000L + rnd.nextInt(2 * MAX_PER_PAGE);

        GridCursor<Long> c = tree.find(null, upperBound);
        Iterator<Long> i = map.headMap(upperBound, true).keySet().iterator();

        Long last = null;

        for (int j = 0; j < off; j++) {
            assertTrue(c.next());

//            X.println(" <-> " + c.get());

            assertEquals(i.next(), c.get());

            last = c.get();
        }

        if (last != null) {
//            X.println(" >-< " + last + " " + upperBound);

            c = tree.find(last, upperBound);

            assertTrue(c.next());
            assertEquals(last, c.get());
        }

        while (c.next()) {
//            X.println(" --> " + c.get());

            assertNotNull(c.get());
            assertEquals(i.next(), c.get());
            assertEquals(c.get(), tree.remove(c.get()));

            i.remove();
        }

        assertEquals(map.size(), size(tree.find(null, null)));
    }

    /**
     * @param c Cursor.
     * @return Number of elements.
     * @throws IgniteCheckedException If failed.
     */
    private static int size(GridCursor<?> c) throws IgniteCheckedException {
        int cnt = 0;

        while(c.next())
            cnt++;

        return cnt;
    }

    /**
     * @param canGetRow Can get row from inner page.
     * @return Test tree instance.
     * @throws IgniteCheckedException If failed.
     */
    protected TestTree createTestTree(boolean canGetRow) throws IgniteCheckedException {
        TestTree tree = new TestTree(reuseList, canGetRow, CACHE_ID, pageMem, allocateMetaPage().pageId());

        assertEquals(0, tree.size());
        assertEquals(0, tree.rootLevel());

        return tree;
    }

    /**
     * @return Allocated meta page ID.
     * @throws IgniteCheckedException If failed.
     */
    private FullPageId allocateMetaPage() throws IgniteCheckedException {
        return new FullPageId(pageMem.allocatePage(CACHE_ID, 0, PageIdAllocator.FLAG_IDX), CACHE_ID);
    }

    /**
     * Test tree.
     */
    protected static class TestTree extends BPlusTree<Long, Long> {
        /**
         * @param reuseList Reuse list.
         * @param canGetRow Can get row from inner page.
         * @param cacheId Cache ID.
         * @param pageMem Page memory.
         * @param metaPageId Meta page ID.
         * @throws IgniteCheckedException If failed.
         */
        public TestTree(ReuseList reuseList, boolean canGetRow, int cacheId, PageMemory pageMem, long metaPageId)
            throws IgniteCheckedException {
            super("test", cacheId, pageMem, null, metaPageId, reuseList,
                new IOVersions<>(new LongInnerIO(canGetRow)), new IOVersions<>(new LongLeafIO()));

            PageIO.registerTest(latestInnerIO(), latestLeafIO());

            initNew();
        }

        /** {@inheritDoc} */
        @Override protected int compare(BPlusIO<Long> io, ByteBuffer buf, int idx, Long n2)
            throws IgniteCheckedException {
            Long n1 = io.getLookupRow(this, buf, idx);

            return Long.compare(n1, n2);
        }

        /** {@inheritDoc} */
        @Override protected Long getRow(BPlusIO<Long> io, ByteBuffer buf, int idx) throws IgniteCheckedException {
            assert io.canGetRow() : io;

            return io.getLookupRow(this, buf, idx);
        }
    }

    /**
     * TODO refactor to use integer in inner page
     * Long inner.
     */
    private static final class LongInnerIO extends BPlusInnerIO<Long> {
        /**
         */
        protected LongInnerIO(boolean canGetRow) {
            super(LONG_INNER_IO, 1, canGetRow, 8);
        }

        /** {@inheritDoc} */
        @Override public int getMaxCount(ByteBuffer buf) {
            if (MAX_PER_PAGE != 0)
                return MAX_PER_PAGE;

            return super.getMaxCount(buf);
        }

        /** {@inheritDoc} */
        @Override public void store(ByteBuffer dst, int dstIdx, BPlusIO<Long> srcIo, ByteBuffer src, int srcIdx)
            throws IgniteCheckedException {
            Long row = srcIo.getLookupRow(null, src, srcIdx);

            store(dst, dstIdx, row, null);
        }

        /**
         * @param row Row.
         */
        private void checkNotRemoved(Long row) {
            if (rmvdIds.contains(row))
                fail("Removed row: " + row);
        }

        /** {@inheritDoc} */
        @Override public void storeByOffset(ByteBuffer buf, int off, Long row) {
            checkNotRemoved(row);

            buf.putLong(off, row);
        }

        /** {@inheritDoc} */
        @Override public Long getLookupRow(BPlusTree<Long,?> tree, ByteBuffer buf, int idx)
            throws IgniteCheckedException {
            Long row = buf.getLong(offset(idx));

            checkNotRemoved(row);

            return row;
        }
    }

    /**
     * @return Page memory.
     */
    protected PageMemory createPageMemory() throws Exception {
        long[] sizes = new long[CPUS];

        for (int i = 0; i < sizes.length; i++)
            sizes[i] = 1024 * MB / CPUS;

        PageMemory pageMem = new PageMemoryNoStoreImpl(log, new UnsafeMemoryProvider(sizes), null, PAGE_SIZE);

        pageMem.start();

        return pageMem;
    }

    /**
     * @return Number of acquired pages.
     */
    protected long acquiredPages() {
        return ((PageMemoryNoStoreImpl)pageMem).acquiredPages();
    }

    /**
     * Long leaf.
     */
    private static final class LongLeafIO extends BPlusLeafIO<Long> {
        /**
         */
        protected LongLeafIO() {
            super(LONG_LEAF_IO, 1, 8);
        }

        /** {@inheritDoc} */
        @Override public int getMaxCount(ByteBuffer buf) {
            if (MAX_PER_PAGE != 0)
                return MAX_PER_PAGE;

            return super.getMaxCount(buf);
        }

        /** {@inheritDoc} */
        @Override public void storeByOffset(ByteBuffer buf, int off, Long row) {
            buf.putLong(off, row);
        }

        /** {@inheritDoc} */
        @Override public void store(ByteBuffer dst, int dstIdx, BPlusIO<Long> srcIo, ByteBuffer src, int srcIdx) {
            assert srcIo == this;

            dst.putLong(offset(dstIdx), src.getLong(offset(srcIdx)));
        }

        /** {@inheritDoc} */
        @Override public Long getLookupRow(BPlusTree<Long,?> tree, ByteBuffer buf, int idx)
            throws IgniteCheckedException {
            return buf.getLong(offset(idx));
        }
    }
}