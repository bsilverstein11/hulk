/*-
 * <<
 * UAVStack
 * ==
 * Copyright (C) 2016 - 2017 UAVStack
 * ==
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * >>
 */

package com.alibaba.ttl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * {@link TransmittableThreadLocal} can transmit value from the thread of submitting task to the thread of executing
 * task.
 * <p>
 * Note: this class extends {@link java.lang.InheritableThreadLocal}, so {@link TransmittableThreadLocal} first is a
 * {@link java.lang.InheritableThreadLocal}.
 *
 * @author Jerry Lee (oldratlee at gmail dot com)
 * @see TtlRunnable
 * @see TtlCallable
 * @since 0.10.0
 */
public class TransmittableThreadLocal<T> extends InheritableThreadLocal<T> {

    /**
     * Computes the value for this transmittable thread-local variable as a function of the source thread's value at the
     * time the task Object is created. This method is called from {@link TtlRunnable} or {@link TtlCallable} when it
     * create, before the task is started.
     * <p>
     * This method merely returns reference of its source thread value, and should be overridden if a different behavior
     * is desired.
     *
     * @since 1.0.0
     */
    protected T copy(T parentValue) {

        return parentValue;
    }

    /**
     * Callback method before task object({@link TtlRunnable}/{@link TtlCallable}) execute.
     * <p>
     * Default behavior is do nothing, and should be overridden if a different behavior is desired.
     * <p>
     * Do not throw any exception, just ignored.
     *
     * @since 1.2.0
     */
    protected void beforeExecute() {

    }

    /**
     * Callback method after task object({@link TtlRunnable}/{@link TtlCallable}) execute.
     * <p>
     * Default behavior is do nothing, and should be overridden if a different behavior is desired.
     * <p>
     * Do not throw any exception, just ignored.
     *
     * @since 1.2.0
     */
    protected void afterExecute() {

    }

    @Override
    public final T get() {

        T value = super.get();
        if (null != value) {
            addValue();
        }
        return value;
    }

    @Override
    public final void set(T value) {

        super.set(value);
        if (null == value) { // may set null to remove value
            removeValue();
        }
        else {
            addValue();
        }
    }

    @Override
    public final void remove() {

        removeValue();
        super.remove();
    }

    /**
     * ????????????????????????threadlocal???
     */
    public final void removeCurrent() {

        super.remove();
    }

    void superRemove() {

        super.remove();
    }

    T copyValue() {

        return copy(get());
    }

    static InheritableThreadLocal<Map<TransmittableThreadLocal<?>, ?>> holder = new InheritableThreadLocal<Map<TransmittableThreadLocal<?>, ?>>() {

        @Override
        protected Map<TransmittableThreadLocal<?>, ?> initialValue() {

            return new WeakHashMap<TransmittableThreadLocal<?>, Object>();
        }

        @Override
        protected Map<TransmittableThreadLocal<?>, ?> childValue(Map<TransmittableThreadLocal<?>, ?> parentValue) {

            return new WeakHashMap<TransmittableThreadLocal<?>, Object>(parentValue);
        }
    };

    void addValue() {

        if (!holder.get().containsKey(this)) {
            holder.get().put(this, null); // WeakHashMap supports null value.
        }
    }

    void removeValue() {

        holder.get().remove(this);
    }

    static Map<TransmittableThreadLocal<?>, Object> copy() {

        Map<TransmittableThreadLocal<?>, Object> copy = new HashMap<TransmittableThreadLocal<?>, Object>();
        for (TransmittableThreadLocal<?> threadLocal : holder.get().keySet()) {
            copy.put(threadLocal, threadLocal.copyValue());
        }
        return copy;
    }

    static Map<TransmittableThreadLocal<?>, Object> backupAndSetToCopied(
            Map<TransmittableThreadLocal<?>, Object> copied) {

        Map<TransmittableThreadLocal<?>, Object> backup = new HashMap<TransmittableThreadLocal<?>, Object>();

        for (Iterator<? extends Map.Entry<TransmittableThreadLocal<?>, ?>> iterator = holder.get().entrySet()
                .iterator(); iterator.hasNext();) {
            Map.Entry<TransmittableThreadLocal<?>, ?> next = iterator.next();
            TransmittableThreadLocal<?> threadLocal = next.getKey();

            // backup
            backup.put(threadLocal, threadLocal.get());

            // clear the TTL value only in copied
            // avoid extra TTL value in copied, when run task.
            if (!copied.containsKey(threadLocal)) {
                iterator.remove();
                threadLocal.superRemove();
            }
        }

        // set value to copied TTL
        for (Map.Entry<TransmittableThreadLocal<?>, Object> entry : copied.entrySet()) {
            @SuppressWarnings("unchecked")
            TransmittableThreadLocal<Object> threadLocal = (TransmittableThreadLocal<Object>) entry.getKey();
            threadLocal.set(entry.getValue());
        }

        // call beforeExecute callback
        doExecuteCallback(true);

        return backup;
    }

    static void restoreBackup(Map<TransmittableThreadLocal<?>, Object> backup) {

        // call afterExecute callback
        doExecuteCallback(false);

        for (Iterator<? extends Map.Entry<TransmittableThreadLocal<?>, ?>> iterator = holder.get().entrySet()
                .iterator(); iterator.hasNext();) {
            Map.Entry<TransmittableThreadLocal<?>, ?> next = iterator.next();
            TransmittableThreadLocal<?> threadLocal = next.getKey();

            // clear the TTL value only in backup
            // avoid the extra value of backup after restore
            if (!backup.containsKey(threadLocal)) {
                iterator.remove();
                threadLocal.superRemove();
            }
        }

        // restore TTL value
        for (Map.Entry<TransmittableThreadLocal<?>, Object> entry : backup.entrySet()) {
            @SuppressWarnings("unchecked")
            TransmittableThreadLocal<Object> threadLocal = (TransmittableThreadLocal<Object>) entry.getKey();
            threadLocal.set(entry.getValue());
        }
    }

    private static void doExecuteCallback(boolean isBefore) {

        for (Map.Entry<TransmittableThreadLocal<?>, ?> entry : holder.get().entrySet()) {
            TransmittableThreadLocal<?> threadLocal = entry.getKey();

            try {
                if (isBefore) {
                    threadLocal.beforeExecute();
                }
                else {
                    threadLocal.afterExecute();
                }
            }
            catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    /**
     * Debug only method!
     */
    static void dump(String title) {

        if (title != null && title.length() > 0) {
            System.out.printf("Start TransmittableThreadLocal[%s] Dump...\n", title);
        }
        else {
            System.out.println("Start TransmittableThreadLocal Dump...");
        }

        for (Map.Entry<TransmittableThreadLocal<?>, ?> entry : holder.get().entrySet()) {
            final TransmittableThreadLocal<?> key = entry.getKey();
            System.out.println(key.get());
        }
        System.out.println("TransmittableThreadLocal Dump end!");
    }

    /**
     * Debug only method!
     */
    static void dump() {

        dump(null);
    }
}
