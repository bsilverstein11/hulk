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

package com.creditease.uav.appserver.listeners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.creditease.agent.helpers.ReflectionHelper;
import com.creditease.agent.helpers.StringHelper;
import com.creditease.monitor.UAVServer;
import com.creditease.monitor.UAVServer.ServerVendor;
import com.creditease.monitor.captureframework.spi.CaptureConstants;
import com.creditease.monitor.globalfilter.AbsGlobalFilterHandler;
import com.creditease.monitor.interceptframework.spi.InterceptConstants;
import com.creditease.monitor.interceptframework.spi.InterceptContext;
import com.creditease.monitor.interceptframework.spi.InterceptContext.Event;
import com.creditease.monitor.interceptframework.spi.InterceptEventListener;
import com.creditease.uav.util.MonitorServerUtil;

public class GlobalFilterDispatchListener extends InterceptEventListener {

    @SuppressWarnings("rawtypes")
    private List<AbsGlobalFilterHandler> handlers = new CopyOnWriteArrayList<AbsGlobalFilterHandler>();

    @SuppressWarnings("rawtypes")
    public void registerHandler(AbsGlobalFilterHandler handler) {

        if (handler == null || handler.getContext() == null) {
            return;
        }

        synchronized (this.handlers) {
            handlers.add(handler);

            int order = handler.getOrder();

            if (order == -1) {
                return;
            }

            /**
             * Descend Order
             */
            // jdk1.7?????????1.7????????????????????????CopyOnWriteArrayList??????????????????sort??????????????????????????????UnsupportedOperationException???
            // ??????jdk1.7??????????????????????????????ArrayList???????????????????????????????????????CopyOnWriteArrayList??????????????????????????????
            ArrayList<AbsGlobalFilterHandler> tempList = new ArrayList<AbsGlobalFilterHandler>(handlers);
            Collections.sort(tempList, new Comparator<AbsGlobalFilterHandler>() {

                @Override
                public int compare(AbsGlobalFilterHandler o1, AbsGlobalFilterHandler o2) {

                    int gan = o1.getOrder() - o2.getOrder();

                    if (gan < 0) {
                        return 1;
                    }
                    else if (gan > 0) {
                        return -1;
                    }

                    return 0;
                }
            });

            handlers.clear();
            handlers.addAll(tempList);
        }
    }

    @SuppressWarnings("rawtypes")
    public void unregisterHandler(String handlerId) {

        if (StringHelper.isEmpty(handlerId)) {
            return;
        }

        int found = -1;
        for (int i = 0; i < handlers.size(); i++) {
            AbsGlobalFilterHandler handler = handlers.get(i);
            if (handler.getId().equals(handlerId)) {
                found = i;
                break;
            }
        }

        if (found > -1) {
            handlers.remove(found);
        }
    }

    @Override
    public boolean isEventListener(Event event) {

        switch (event) {
            case GLOBAL_FILTER_REQUEST:
            case GLOBAL_FILTER_RESPONSE:
                return true;
            default:
                break;

        }
        return false;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void handleEvent(InterceptContext context) {

        UAVServer.ServerVendor vendor = (ServerVendor) UAVServer.instance()
                .getServerInfo(CaptureConstants.INFO_APPSERVER_VENDOR);

        Object httpReq = context.get(InterceptConstants.HTTPREQUEST);

        Object httpResp = context.get(InterceptConstants.HTTPRESPONSE);

        String url = null;
        /**
         * MSCP
         */
        if (vendor == ServerVendor.MSCP) {
            // TODO
        }
        /**
         * JEE Application uses HttpServletRequest
         */
        else {
            StringBuffer urlSB = (StringBuffer) ReflectionHelper.invoke("javax.servlet.http.HttpServletRequest",
                    httpReq, "getRequestURL", null, null);
            if (urlSB != null) {
                url = urlSB.toString();
            }
        }

        if (url == null) {
            return;
        }

        if (MonitorServerUtil.isIncludeMonitorURL(url) == false) {
            return;
        }

        /**
         * NOTE: ??????????????????????????????????????????????????????
         * 
         * 1. ??????1???BaseGlobalFilterHandler????????????Handler Chain????????????Filter
         * Chain?????????handler???isBlockHandlerChain???isBlockFilterChain??????true
         * 
         * 2. ????????????BaseGlobalFilterHandler???contextPath?????????????????????????????????Handler Chain???????????????Filter
         * Chain???????????????BaseGlobalFilterHandler???isBlockHandlerChain???true???
         * ?????????BaseGlobalFilterHandler???isBlockFilterChain???false
         * 
         * 3. ????????????BaseGlobalFilterHandler???contextPath?????????????????????????????????Handler Chain???????????????Filter
         * Chain???????????????BaseGlobalFilterHandler???isBlockHandlerChain???true???
         * ??????????????????BaseGlobalFilterHandler???isBlockFilterChain???true
         * 
         * ??????????????????BaseGlobalFilterHandler???????????????????????????????????????
         */
        boolean isBlockFilterChain = false;

        for (AbsGlobalFilterHandler handler : handlers) {

            String contextPath = handler.getContext();
            if (url.indexOf(contextPath) > -1) {
                try {
                    handler.handle(httpReq, httpResp, context);
                }
                catch (Exception e) {
                    logger.error("GlobalFilterDispatchListener Handler[" + handler.getClass().getName()
                            + "] runs FAIL: url=" + url, e);
                }
                if (handler.isBlockFilterChain() == true) {
                    isBlockFilterChain = true;
                }

                if (handler.isBlockHandlerChain() == true) {
                    break;
                }
            }
        }

        if (isBlockFilterChain == true) {
            context.put(InterceptConstants.STOPREQUEST, new Object());
        }
    }

}
