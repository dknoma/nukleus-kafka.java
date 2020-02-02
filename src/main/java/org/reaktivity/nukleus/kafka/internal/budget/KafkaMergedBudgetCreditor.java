/**
 * Copyright 2016-2019 The Reaktivity Project
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
package org.reaktivity.nukleus.kafka.internal.budget;

import java.util.function.LongSupplier;

import org.agrona.collections.Long2ObjectHashMap;

public final class KafkaMergedBudgetCreditor implements MergedBudgetCreditor
{
    private final Long2ObjectHashMap<KafkaMergedBudget> budgetsByMergedId;
    private final LongSupplier supplyBudgetId;

    KafkaMergedBudgetCreditor(
        Long2ObjectHashMap<KafkaMergedBudget> budgetsByMergedId,
        LongSupplier supplyBudgetId)
    {
        this.budgetsByMergedId = budgetsByMergedId;
        this.supplyBudgetId = supplyBudgetId;
    }

    @Override
    public long acquire(
        long budgetId)
    {
        return acquire(0L, budgetId);
    }

    @Override
    public long acquire(
        long watcherId,
        long budgetId)
    {
        assert watcherId != 0L;
        final long mergedBudgetId = supplyBudgetId.getAsLong();
        budgetsByMergedId.put(mergedBudgetId, new KafkaMergedBudget(budgetId, watcherId));
        return mergedBudgetId;
    }

    @Override
    public long credit(
        long traceId,
        long mergedBudgetId,
        long credit)
    {
        final KafkaMergedBudget mergedBudget = budgetsByMergedId.get(mergedBudgetId);
        assert mergedBudget != null;

        return mergedBudget.credit(traceId, credit);
    }

    @Override
    public void release(
        long mergedBudgetId)
    {
        final KafkaMergedBudget mergedBudget = budgetsByMergedId.remove(mergedBudgetId);
        assert mergedBudget != null;
    }
}