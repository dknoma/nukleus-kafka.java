/**
 * Copyright 2016-2020 The Reaktivity Project
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
package org.reaktivity.nukleus.kafka.internal.stream;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.reaktivity.nukleus.budget.BudgetDebitor;
import org.reaktivity.nukleus.buffer.BufferPool;
import org.reaktivity.nukleus.kafka.internal.KafkaConfiguration;
import org.reaktivity.nukleus.kafka.internal.KafkaCounters;
import org.reaktivity.nukleus.kafka.internal.memory.MemoryManager;
import org.reaktivity.nukleus.kafka.internal.util.DelayedTaskScheduler;
import org.reaktivity.nukleus.route.RouteManager;
import org.reaktivity.nukleus.stream.StreamFactory;
import org.reaktivity.nukleus.stream.StreamFactoryBuilder;

public final class ClientStreamFactoryBuilder implements StreamFactoryBuilder
{
    private final KafkaConfiguration config;
    private final Function<KafkaCounters, MemoryManager> supplyMemoryManager;
    private final Consumer<LongFunction<NetworkConnectionPool>> connectPoolFactoryConsumer;
    private final Long2ObjectHashMap<NetworkConnectionPool.AbstractNetworkConnection> correlations;
    private final Long2ObjectHashMap<NetworkConnectionPool> connectionPools;
    private final DelayedTaskScheduler scheduler;

    private RouteManager router;
    private MutableDirectBuffer writeBuffer;
    private LongUnaryOperator supplyInitialId;
    private LongUnaryOperator supplyReplyId;
    private LongSupplier supplyTraceId;
    private Supplier<BufferPool> supplyBufferPool;
    private Function<String, LongSupplier> supplyCounter;
    private Function<String, LongConsumer> supplyAccumulator;
    private LongFunction<BudgetDebitor> supplyDebitor;
    private KafkaCounters counters;
    private ToIntFunction<String> supplyTypeId;

    public ClientStreamFactoryBuilder(
        KafkaConfiguration config,
        Function<KafkaCounters, MemoryManager> supplyMemoryManager,
        Long2ObjectHashMap<NetworkConnectionPool> connectionPools,
        Consumer<LongFunction<NetworkConnectionPool>> connectPoolFactoryConsumer,
        DelayedTaskScheduler scheduler)
    {
        this.config = config;
        this.supplyMemoryManager = supplyMemoryManager;
        this.connectPoolFactoryConsumer = connectPoolFactoryConsumer;
        this.correlations = new Long2ObjectHashMap<>();
        this.connectionPools = connectionPools;
        this.scheduler = scheduler;
    }

    @Override
    public ClientStreamFactoryBuilder setRouteManager(
        RouteManager router)
    {
        this.router = router;
        return this;
    }

    @Override
    public ClientStreamFactoryBuilder setWriteBuffer(
        MutableDirectBuffer writeBuffer)
    {
        this.writeBuffer = writeBuffer;
        return this;
    }

    @Override
    public ClientStreamFactoryBuilder setInitialIdSupplier(
        LongUnaryOperator supplyInitialId)
    {
        this.supplyInitialId = supplyInitialId;
        return this;
    }

    @Override
    public StreamFactoryBuilder setReplyIdSupplier(
        LongUnaryOperator supplyReplyId)
    {
        this.supplyReplyId = supplyReplyId;
        return this;
    }

    @Override
    public ClientStreamFactoryBuilder setTraceIdSupplier(
        LongSupplier supplyTraceId)
    {
        this.supplyTraceId = supplyTraceId;
        return this;
    }

    @Override
    public StreamFactoryBuilder setTypeIdSupplier(
        ToIntFunction<String> supplyTypeId)
    {
        this.supplyTypeId = supplyTypeId;
        return this;
    }

    @Override
    public StreamFactoryBuilder setBufferPoolSupplier(
        Supplier<BufferPool> supplyBufferPool)
    {
        this.supplyBufferPool = supplyBufferPool;
        return this;
    }

    @Override
    public StreamFactoryBuilder setCounterSupplier(
        Function<String, LongSupplier> supplyCounter)
    {
        this.supplyCounter = supplyCounter;
        return this;
    }

    @Override
    public StreamFactoryBuilder setAccumulatorSupplier(
            Function<String, LongConsumer> supplyAccumulator)
    {
        this.supplyAccumulator = supplyAccumulator;
        return this;
    }

    @Override
    public StreamFactoryBuilder setBudgetDebitorSupplier(
        LongFunction<BudgetDebitor> supplyDebitor)
    {
        this.supplyDebitor = supplyDebitor;
        return this;
    }

    @Override
    public StreamFactory build()
    {
        if (counters == null)
        {
            counters = new KafkaCounters(supplyCounter, supplyAccumulator);
        }

        final BufferPool bufferPool = supplyBufferPool.get();
        final MemoryManager memoryManager = supplyMemoryManager.apply(counters);

        return new ClientStreamFactory(
                config,
                router,
                writeBuffer,
                bufferPool,
                memoryManager,
                supplyInitialId,
                supplyReplyId,
                supplyTraceId,
                supplyTypeId,
                supplyCounter,
                supplyDebitor,
                correlations,
                connectionPools,
                connectPoolFactoryConsumer,
                scheduler, counters);
    }
}
