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

package com.creditease.uav.hook.redis.lettuce.invokeChain;

import com.creditease.agent.helpers.DataConvertHelper;
import com.creditease.uav.apm.invokechain.spi.InvokeChainAdapter;
import com.creditease.uav.apm.invokechain.spi.InvokeChainConstants;
import com.creditease.uav.apm.invokechain.spi.InvokeChainContext;

public class LettuceClientAdapter extends InvokeChainAdapter {

    @Override
    public void beforePreCap(InvokeChainContext context, Object[] args) {

        String clazz = "com.lambdaworks.redis.RedisAsyncConnectionImpl";
        int num = -1;
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        for (int i = 0; i < stacktrace.length; i++) {
            if (stacktrace[i].getClassName().equals(clazz) && stacktrace.length >= i + 1
                    && !stacktrace[i + 1].getClassName().equals(clazz)) {
                num = i;
                break;
            }
        }
        StackTraceElement a = stacktrace[num + 1];
        if (a.getClassName().equals("sun.reflect.NativeMethodAccessorImpl")) {
            context.put(InvokeChainConstants.CLIENT_IT_KEY, DataConvertHelper
                    .toInt(System.getProperty("com.creditease.uav.invokechain.code.redis.lettuce.key.1"), 0));
            context.put(InvokeChainConstants.CLIENT_IT_CLASS,
                    System.getProperty("com.creditease.uav.invokechain.code.redis.lettuce.class.1"));
        }
        else {
            context.put(InvokeChainConstants.CLIENT_IT_KEY, DataConvertHelper
                    .toInt(System.getProperty("com.creditease.uav.invokechain.code.redis.lettuce.key.2"), 0));
            context.put(InvokeChainConstants.CLIENT_IT_CLASS,
                    System.getProperty("com.creditease.uav.invokechain.code.redis.lettuce.class.2"));
        }

    }

    @Override
    public void afterPreCap(InvokeChainContext context, Object[] args) {

        // ??????redis????????????????????????????????????
    }

    @Override
    public void beforeDoCap(InvokeChainContext context, Object[] args) {

    }

    @Override
    public void afterDoCap(InvokeChainContext context, Object[] args) {

    }

}
