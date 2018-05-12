/**
 * Copyright 2016-2017 The Reaktivity Project
 *
 * The Reaktivity Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.reaktivity.nukleus.kafka.internal.cache;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.junit.Rule;
import org.junit.Test;
import org.reaktivity.nukleus.kafka.internal.memory.MemoryManager;
import org.reaktivity.nukleus.kafka.internal.stream.HeadersFW;
import org.reaktivity.nukleus.kafka.internal.types.MessageFW;
import org.reaktivity.nukleus.kafka.internal.types.OctetsFW;

public final class MessageCacheTest
{
    private MemoryManager memoryManager;
    private MutableDirectBuffer memoryBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1000));


    private DirectBuffer key = asBuffer("key");
    private DirectBuffer headersBuffer = new UnsafeBuffer(new byte[0]);
    private HeadersFW headers = new HeadersFW().wrap(headersBuffer, 0, 0);
    private DirectBuffer value = asBuffer("value");
    private final MessageFW messageRO = new MessageFW();
    private final MessageFW.Builder messageRW = new MessageFW.Builder();
    private MutableDirectBuffer expectedBuffer = new UnsafeBuffer(new byte[100]);

    private MessageFW expected = messageRW
            .wrap(expectedBuffer, Integer.BYTES, expectedBuffer.capacity())
            .timestamp(123)
            .traceId(456)
            .key(key, 0, key.capacity())
            .headers((OctetsFW) null)
            .value(value, 0, value.capacity())
            .build();

    @Rule
    public JUnitRuleMockery context = new JUnitRuleMockery()
    {
        {
            memoryManager = mock(MemoryManager.class);
        }
    };

    private MessageCache cache = new MessageCache(memoryManager);

    @Test
    public void shouldPutMessage()
    {
        int size = expected.sizeof();
        context.checking(new Expectations()
        {
            {
                oneOf(memoryManager).acquire(size + Integer.BYTES);
                will(returnValue(0L));
                oneOf(memoryManager).resolve(0L);
                will(returnValue(memoryBuffer.addressOffset()));
            }
        });
        int handle = cache.put(123, 456, key, headers, value);
        assertNotEquals(MessageCache.NO_MESSAGE, handle);
    }

    @Test
    public void shouldGetMessage()
    {
        int size = expected.sizeof();
        context.checking(new Expectations()
        {
            {
                oneOf(memoryManager).acquire(size + Integer.BYTES);
                will(returnValue(0L));
                oneOf(memoryManager).resolve(0L);
                will(returnValue(memoryBuffer.addressOffset()));
                oneOf(memoryManager).resolve(0L);
                will(returnValue(memoryBuffer.addressOffset()));
            }
        });
        int handle = cache.put(123, 456, key, headers, value);
        MessageFW message = cache.get(handle, messageRO);
        assertEquals(123, message.timestamp());
        assertEquals(456, message.traceId());
    }

    @Test
    public void shouldReleaseMessageAndReuseHandle()
    {
        int size = expected.sizeof() + Integer.BYTES;
        context.checking(new Expectations()
        {
            {
                oneOf(memoryManager).acquire(size);
                will(returnValue(0L));
                oneOf(memoryManager).resolve(0L);
                will(returnValue(memoryBuffer.addressOffset()));

                oneOf(memoryManager).resolve(0L);
                will(returnValue(memoryBuffer.addressOffset()));

                oneOf(memoryManager).release(0L, size);
                oneOf(memoryManager).acquire(size + 1);
                will(returnValue(0L));
                oneOf(memoryManager).resolve(0L);
                will(returnValue(memoryBuffer.addressOffset()));
                oneOf(memoryManager).resolve(0L);
                will(returnValue(memoryBuffer.addressOffset()));

                oneOf(memoryManager).resolve(0L);
                will(returnValue(memoryBuffer.addressOffset()));
            }
        });
        int handle = cache.put(123, 456, key, headers, value);
        MessageFW message = cache.get(handle, messageRO);
        assertEquals(123, message.timestamp());
        assertEquals(456, message.traceId());
        int bytesReleased = cache.release(handle);
        assertEquals(size, bytesReleased);
        int handle2 = cache.put(124, 457, asBuffer("key2"), headers, value);
        assertEquals(handle, handle2);
        message = cache.get(handle2, messageRO);
        assertEquals(124, message.timestamp());
        assertEquals(457, message.traceId());
    }

    @Test
    public void shouldReplaceMessage()
    {
        int size = expected.sizeof() + Integer.BYTES;
        context.checking(new Expectations()
        {
            {
                oneOf(memoryManager).acquire(size);
                will(returnValue(0L));
                oneOf(memoryManager).resolve(0L);
                will(returnValue(memoryBuffer.addressOffset()));

                oneOf(memoryManager).resolve(0L);
                will(returnValue(memoryBuffer.addressOffset()));

                oneOf(memoryManager).release(0L, size);
                oneOf(memoryManager).acquire(size + 1);
                will(returnValue(0L));
                oneOf(memoryManager).resolve(0L);
                will(returnValue(memoryBuffer.addressOffset()));
                oneOf(memoryManager).resolve(0L);
                will(returnValue(memoryBuffer.addressOffset()));

                oneOf(memoryManager).resolve(0L);
                will(returnValue(memoryBuffer.addressOffset()));
            }
        });
        int handle = cache.put(123, 456, key, headers, value);
        MessageFW message = cache.get(handle, messageRO);
        assertEquals(123, message.timestamp());
        assertEquals(456, message.traceId());
        int handle2 = cache.replace(handle, 124, 457, asBuffer("key2"), headers, value);
        assertEquals(handle, handle2);
        message = cache.get(handle2, messageRO);
        assertEquals(124, message.timestamp());
        assertEquals(457, message.traceId());
    }

    @Test
    public void shouldEvictLruMessageAndNotReuseEvictedHandleUntilReleased()
    {
        int size = expected.sizeof() + Integer.BYTES;
        final long address1 = 0L;
        final long address2 = 100L;
        final long address3 = 200L;
        final long address4 = 300L;

        context.checking(new Expectations()
        {
            {
                oneOf(memoryManager).acquire(size);
                will(returnValue(address1));
                oneOf(memoryManager).resolve(address1);
                will(returnValue(memoryBuffer.addressOffset() + address1));

                oneOf(memoryManager).acquire(size);
                will(returnValue(address2));
                oneOf(memoryManager).resolve(address2);
                will(returnValue(memoryBuffer.addressOffset() + address2));

                oneOf(memoryManager).resolve(address1);
                will(returnValue(memoryBuffer.addressOffset() + address1));

                oneOf(memoryManager).acquire(size);
                will(returnValue(MemoryManager.OUT_OF_MEMORY));
                oneOf(memoryManager).release(address2, size);
                oneOf(memoryManager).acquire(size);
                will(returnValue(address2));
                oneOf(memoryManager).resolve(address2);
                will(returnValue(memoryBuffer.addressOffset() + address2));
                oneOf(memoryManager).resolve(address2);
                will(returnValue(memoryBuffer.addressOffset() + address2));

                oneOf(memoryManager).resolve(address1);
                will(returnValue(memoryBuffer.addressOffset() + address1));
                oneOf(memoryManager).resolve(address2);
                will(returnValue(memoryBuffer.addressOffset() + address2));

                oneOf(memoryManager).acquire(size);
                will(returnValue(address3));
                oneOf(memoryManager).resolve(address3);
                will(returnValue(memoryBuffer.addressOffset() + address3));
                oneOf(memoryManager).resolve(address3);
                will(returnValue(memoryBuffer.addressOffset() + address3));

                oneOf(memoryManager).acquire(size);
                will(returnValue(address4));
                oneOf(memoryManager).resolve(address4);
                will(returnValue(memoryBuffer.addressOffset() + address4));
            }
        });
        int handle1 = cache.put(123, 456, key, headers, value);
        int handle2 = cache.put(124, 457, key, headers, value);
        assertNotNull(cache.get(handle1, messageRO));

        int handle3 = cache.put(125, 458, key, headers, value);
        MessageFW message = cache.get(handle1, messageRO);
        assertNotNull(message);
        assertNull(cache.get(handle2, messageRO));
        message = cache.get(handle3, messageRO);
        assertEquals(125, message.timestamp());
        assertEquals(458, message.traceId());

        // handle2 was evicted, not released, so must not be reused
        int handle4 = cache.put(126, 459, key, headers, value);
        assertNull(cache.get(handle2, messageRO));
        message = cache.get(handle4, messageRO);
        assertEquals(126, message.timestamp());
        assertEquals(459, message.traceId());

        // handle2 should be reused once it's released
        cache.release(handle2);
        int handle5 = cache.put(127, 460, key, headers, value);
        assertEquals(handle2, handle5);
    }

    private static DirectBuffer asBuffer(String value)
    {
        byte[] bytes = value.getBytes(UTF_8);
        return new UnsafeBuffer(bytes);
    }

}
