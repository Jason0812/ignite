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

package org.apache.ignite.internal.binary;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.ignite.configuration.BinaryConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.util.IgniteUtils;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.marshaller.MarshallerContextTestImpl;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;

/**
 *
 */
public class BinaryFieldExtractionSelfTest extends GridCommonAbstractTest {
    /**
     * Create marshaller.
     *
     * @return Binary marshaller.
     * @throws Exception If failed.
     */
    protected BinaryMarshaller createMarshaller() throws Exception {
        BinaryContext ctx = new BinaryContext(BinaryCachingMetadataHandler.create(), new IgniteConfiguration(),
            log());

        BinaryMarshaller marsh = new BinaryMarshaller();

        BinaryConfiguration bCfg = new BinaryConfiguration();

        IgniteConfiguration iCfg = new IgniteConfiguration();

        iCfg.setBinaryConfiguration(bCfg);

        marsh.setContext(new MarshallerContextTestImpl(null));

        IgniteUtils.invoke(BinaryMarshaller.class, marsh, "setBinaryContext", ctx, iCfg);

        return marsh;
    }

    /**
     * @throws Exception If failed.
     */
    public void testPrimitiveMarshalling() throws Exception {
        BinaryMarshaller marsh = createMarshaller();

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        TestObject obj = new TestObject(0);

        BinaryObjectImpl binObj = toBinary(obj, marsh);

        BinaryFieldEx[] fields = new BinaryFieldEx[] {
            (BinaryFieldEx)binObj.type().field("bVal"),
            (BinaryFieldEx)binObj.type().field("cVal"),
            (BinaryFieldEx)binObj.type().field("sVal"),
            (BinaryFieldEx)binObj.type().field("iVal"),
            (BinaryFieldEx)binObj.type().field("lVal"),
            (BinaryFieldEx)binObj.type().field("fVal"),
            (BinaryFieldEx)binObj.type().field("dVal")
        };

        ByteBuffer buf = ByteBuffer.allocate(1024 * 1024);

        for (int i = 0; i < 100; i++) {
            TestObject to = new TestObject(rnd.nextLong());

            BinaryObjectImpl bObj = toBinary(to, marsh);

            for (BinaryFieldEx field : fields)
                field.writeField(bObj, buf);

            buf.flip();

            for (BinaryFieldEx field : fields)
                assertEquals(field.value(bObj), field.readField(buf));

            buf.flip();
        }
    }

    /**
     * @param obj Object to transform to a binary object.
     * @param marsh Binary marshaller.
     * @return Binary object.
     */
    protected BinaryObjectImpl toBinary(Object obj, BinaryMarshaller marsh) throws Exception {
        byte[] bytes = marsh.marshal(obj);

        return new BinaryObjectImpl(binaryContext(marsh), bytes, 0);
    }

    /**
     * Get binary context for the current marshaller.
     *
     * @param marsh Marshaller.
     * @return Binary context.
     */
    protected static BinaryContext binaryContext(BinaryMarshaller marsh) {
        GridBinaryMarshaller impl = U.field(marsh, "impl");

        return impl.context();
    }

    /**
     *
     */
    @SuppressWarnings("UnusedDeclaration")
    private static class TestObject {
        /** */
        private byte bVal;

        /** */
        private char cVal;

        /** */
        private short sVal;

        /** */
        private int iVal;

        /** */
        private long lVal;

        /** */
        private float fVal;

        /** */
        private double dVal;

        /**
         * @param seed Seed.
         */
        private TestObject(long seed) {
            bVal = (byte)seed;
            cVal = (char)seed;
            sVal = (short)seed;
            iVal = (int)seed;
            lVal = seed;
            fVal = seed;
            dVal = seed;
        }
    }
}
