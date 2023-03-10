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

package com.creditease.uav.hook.httpclients.sync.interceptors;

import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.protocol.HttpContext;

import com.creditease.monitor.UAVServer;
import com.creditease.monitor.captureframework.spi.CaptureConstants;
import com.creditease.monitor.captureframework.spi.Monitor;
import com.creditease.uav.apm.invokechain.spi.InvokeChainConstants;
import com.creditease.uav.common.BaseComponent;
import com.creditease.uav.hook.httpclients.sync.invokeChain.ApacheHttpClientAdapter;
import com.creditease.uav.util.MonitorServerUtil;

/**
 * 
 * HttpClientProxy description: this is the real work class who does all interceptions
 *
 */
public class ApacheHttpClientIT extends BaseComponent {

    private static ThreadLocal<ApacheHttpClientIT> tl = new ThreadLocal<ApacheHttpClientIT>();

    /**
     * use threadlocal to pass the ApacheHttpClientIT Object is more safe with bytecode weave
     * 
     * @param appid
     * @param args
     */
    public static void start(String appid, Object[] args) {

        ApacheHttpClientIT m = new ApacheHttpClientIT(appid);
        tl.set(m);

        m.doStart(args);
    }

    /**
     * use threadlocal to pass the ApacheHttpClientIT Object is more safe with bytecode weave
     * 
     * @param args
     */
    public static void end(Object[] args) {

        ApacheHttpClientIT m = tl.get();

        m.doEnd(args);

        tl.remove();
    }

    private String applicationId;
    private String targetURl;
    private Map<String, Object> ivcContextParams = new HashMap<String, Object>();

    public ApacheHttpClientIT(String appid) {
        this.applicationId = appid;
    }

    /**
     * for http client
     * 
     * @param args
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unused", "unchecked" })
    public void doStart(Object[] args) {

        HttpRequest request = null;
        HttpContext context = null;
        HttpHost target = null;
        Map mObj = null;
        if (args.length == 3) {
            target = (HttpHost) args[0];
            request = (HttpRequest) args[1];
            context = (HttpContext) args[2];
        }

        String httpAction = null;
        // String targetURL = null;
        /**
         * ??????????????????UAV????????????????????????http header???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
         * 
         * ?????????????????????????????????????????????
         */
        // ??????httpclient?????????header????????????????????????????????????
        request.removeHeaders("UAV-Client-Src");
        request.addHeader("UAV-Client-Src", MonitorServerUtil.getUAVClientSrc(this.applicationId));

        RequestLine rl = request.getRequestLine();
        httpAction = rl.getMethod();
        targetURl = rl.getUri();

        // ??????HttpRequest?????????ip:port????????????httpHost??????????????????
        if (!targetURl.startsWith("http")) {
            targetURl = target.toURI() + targetURl;
        }

        Map<String, Object> params = new HashMap<String, Object>();

        params.put(CaptureConstants.INFO_CLIENT_REQUEST_URL, targetURl);
        params.put(CaptureConstants.INFO_CLIENT_REQUEST_ACTION, httpAction);
        params.put(CaptureConstants.INFO_CLIENT_APPID, this.applicationId);
        params.put(CaptureConstants.INFO_CLIENT_TYPE, "apache.http.Client");

        if (logger.isDebugable()) {
            logger.debug("Invoke START:" + targetURl + "," + httpAction + "," + this.applicationId, null);
        }

        UAVServer.instance().runMonitorCaptureOnServerCapPoint(CaptureConstants.CAPPOINT_APP_CLIENT,
                Monitor.CapturePhase.PRECAP, params);

        // register adapter
        UAVServer.instance().runSupporter("com.creditease.uav.apm.supporters.InvokeChainSupporter", "registerAdapter",
                ApacheHttpClientAdapter.class);

        ivcContextParams = (Map<String, Object>) UAVServer.instance().runSupporter(
                "com.creditease.uav.apm.supporters.InvokeChainSupporter", "runCap",
                InvokeChainConstants.CHAIN_APP_CLIENT, InvokeChainConstants.CapturePhase.PRECAP, params,
                ApacheHttpClientAdapter.class, args);
    }

    /**
     * for http client
     * 
     * @param args
     * @return
     */
    public void doEnd(Object[] args) {

        Map<String, Object> params = new HashMap<String, Object>();

        String server = "";

        int rc = -1;

        String responseState = "";

        if (Throwable.class.isAssignableFrom(args[0].getClass())) {

            Throwable e = (Throwable) args[0];

            responseState = e.toString();
        }
        else {
            HttpResponse response = (HttpResponse) args[0];

            Header sheader = response.getLastHeader("Server");

            if (sheader != null) {
                server = sheader.getValue();
            }

            responseState = response.getStatusLine().getStatusCode() + "";

            rc = 1;
        }

        if (logger.isDebugable()) {
            logger.debug("Invoke END:" + rc + "," + server, null);
        }

        params.put(CaptureConstants.INFO_CLIENT_TARGETSERVER, server);
        params.put(CaptureConstants.INFO_CLIENT_RESPONSECODE, rc);
        params.put(CaptureConstants.INFO_CLIENT_TYPE, "apache.http.Client");
        params.put(CaptureConstants.INFO_CLIENT_APPID, this.applicationId);
        params.put(CaptureConstants.INFO_CLIENT_REQUEST_URL, targetURl);
        params.put(CaptureConstants.INFO_CLIENT_RESPONSESTATE, responseState);

        UAVServer.instance().runMonitorCaptureOnServerCapPoint(CaptureConstants.CAPPOINT_APP_CLIENT,
                Monitor.CapturePhase.DOCAP, params);

        if (ivcContextParams != null) {
            ivcContextParams.putAll(params);
        }

        UAVServer.instance().runSupporter("com.creditease.uav.apm.supporters.InvokeChainSupporter", "runCap",
                InvokeChainConstants.CHAIN_APP_CLIENT, InvokeChainConstants.CapturePhase.DOCAP, ivcContextParams,
                ApacheHttpClientAdapter.class, args);
    }
}
