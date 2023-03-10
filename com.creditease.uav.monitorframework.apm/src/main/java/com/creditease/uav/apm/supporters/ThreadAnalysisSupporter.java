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

package com.creditease.uav.apm.supporters;

import java.io.File;

import com.creditease.agent.helpers.DataConvertHelper;
import com.creditease.agent.helpers.DateTimeHelper;
import com.creditease.agent.helpers.IOHelper;
import com.creditease.agent.helpers.JVMToolHelper;
import com.creditease.agent.helpers.NetworkHelper;
import com.creditease.agent.helpers.RuntimeHelper;
import com.creditease.agent.helpers.StringHelper;
import com.creditease.monitor.UAVMonitor;
import com.creditease.monitor.UAVServer;
import com.creditease.monitor.captureframework.spi.CaptureConstants;
import com.creditease.uav.common.Supporter;

public class ThreadAnalysisSupporter extends Supporter {

    private UAVMonitor monitor = new UAVMonitor(logger, 60000);

    private static final String SUPPORTED_METHOD = "captureJavaThreadAnalysis";

    private static Object lock = new Object();
    private static final long FROZON_TIME_LIMIT = 1000L;
    private static volatile long lastInvokeTime = System.currentTimeMillis();

    private final String SYMBOL = "_";

    @Override
    public void start() {

    }

    @Override
    public void stop() {

        super.stop();
    }

    @Override
    public Object run(String methodName, Object... params) {

        long now = System.currentTimeMillis();

        /**
         * concurrency control
         */
        long lastTime = lastInvokeTime;
        lastInvokeTime = now;
        if (now + FROZON_TIME_LIMIT < lastTime) {
            return "ERR:BE RUNNING";
        }

        // valid arguments
        if (!SUPPORTED_METHOD.equals(methodName) || params == null || params.length < 4) {
            return "ERR:ILLEGAL ARGUMENT";
        }

        Object ret = captureJavaThreadAnalysis(params);

        lastInvokeTime = now;
        monitor.logPerf(now, "THREAD_ANALYSIS");
        return ret;
    }

    private Object captureJavaThreadAnalysis(Object... params) {

        // ?????????
        String pid = (String) params[0];
        if (StringHelper.isEmpty(pid)) {
            // ??????????????????????????????????????????????????????MOF???????????????PID
            pid = JVMToolHelper.getCurrentProcId();
        }

        // ???????????????(??????????????????)
        long execTime = DataConvertHelper.toLong(params[1], -1);
        if (execTime == -1) {
            execTime = System.currentTimeMillis();
        }

        // IP??????????????????????????????MOF???????????????IP
        String ip = (String) params[2];
        if (StringHelper.isEmpty(ip)) {
            ip = NetworkHelper.getLocalIP();
        }

        // ????????????
        String fileBase = (String) params[3];
        if (StringHelper.isEmpty(fileBase)) {
            return "ERR:NO STORE FILE BASE";
        }

        // ?????????
        String port = UAVServer.instance().getServerInfo(CaptureConstants.INFO_APPSERVER_LISTEN_PORT) + "";

        boolean isWin = JVMToolHelper.isWindows();
        String jstackPath = getJdkJstackPath(isWin);
        if (null == jstackPath) {
            // ?????????????????????java_home???????????????
            return "ERR:NO JDK";
        }

        if (!checkDirPermission(fileBase)) {
            return "ERR:FILE PERMISSION DENIED";
        }

        String cmd = "";
        String dateTime = DateTimeHelper.toFormat("yyyy-MM-dd_HH-mm-ss.SSS", execTime);
        // ?????????????????????????????????
        String name = ip + SYMBOL + port + SYMBOL + dateTime + ".log";
        String file = fileBase + "/" + name;

        // ???????????????????????????????????????????????????
        if (isWin) {
            cmd = jstackPath + " " + pid + " >> " + file;        
        }
        else {
            cmd = " top -Hp " + pid + " bn 1 > " + file + " && echo '=====' >> " + file + " && " + jstackPath 
                    + " " + pid + " >>  " + file;
        }

        // ?????????????????????????????????????????????????????????????????????????????????
        if (logger.isDebugable()) {
            logger.debug("RUN Java Thread Analysis START: pid=" + pid, null);
        }

        try {
            synchronized (lock) {
                if (isWin) {
                    RuntimeHelper.exec(10000, "cmd.exe", "/C", cmd);
                }
                else {
                    RuntimeHelper.exec(10000, "/bin/bash", "-c", cmd);
                }
            }
        }
        catch (Exception e) {
            logger.warn("RUN Java Thread Analysis FAIL: ", e);
        }
        // ??????????????????????????????????????????????????????????????????
        if (!IOHelper.exists(file)) {
            logger.warn("RUN Java Thread Analysis FAIL: file[" + file + "] not exist", null);
            return "ERR:FILE NOT EXIST";
        }

        /**
         * FIX: ????????????????????????????????????????????????inode???????????????inode???????????????????????????????????????
         */
        if (!deleteFileByFuzzyName(fileBase, SYMBOL + port + SYMBOL, name)) {
            return "ERR:FILE PERMISSION DENIED";
        }

        return file;

    }

    /**
     * deleteFileByFuzzyName
     * 
     * @param parentPath
     * @param fuzzyName
     * @return
     */
    private boolean deleteFileByFuzzyName(String parentPath, String fuzzyName, String filter) {

        try {

            File dir = new File(parentPath);
            if (dir.exists()) {
                File[] files = dir.listFiles();
                for (File file : files) {
                    if (file.isFile() && file.getName().contains(fuzzyName) && !file.getName().equals(filter)) {
                        file.delete();
                    }
                }
            }
        }
        catch (Exception e) {
            logger.warn("In " + parentPath + " can not delete fuzzy file " + fuzzyName, e);
            return false;
        }
        return true;
    }

    private boolean checkDirPermission(String dir) {

        try {
            File f = new File(dir);
            return f.canWrite();
        }
        catch (Exception e) {
            return false;
        }
    }

    private String getJdkBinPath() {

        // ??????java_home??????
        String javahome = System.getProperty("java.home");
        // ??????????????????/???????????????????????????
        if (!javahome.endsWith("/")) {
            javahome = javahome + "/";
        }
        // ??????java_home bin?????????
        return javahome + "../bin";
    }
    
    private String findJstackPath(String findPath, Boolean isWindow) {
        
        File findDir = new File(findPath);
        File[] files = findDir.listFiles();
        for (File file : files) {
            if (file.isDirectory() && file.getName().toLowerCase().contains("jdk")) {
                String tmpPath = file.getAbsolutePath() + "/bin/" + "jstack";
                if(isWindow) {
                    tmpPath += ".exe";
                }
                File tmpFile = new File(tmpPath);
                if(tmpFile.exists()) {
                    return tmpPath;
                }
            }
        }
        return null;
    }
    
    private String getJdkJstackPath(boolean isWin) {

        // ??????java_home??????
        String javahome = System.getProperty("java.home");
        // ??????????????????/???????????????????????????
        if (!javahome.endsWith("/")) {
            javahome = javahome + "/";
        }
        
        // ??????java_home bin?????????
        String jstackPath = javahome + "../bin/" + "jstack";
        if(isWin) {
            jstackPath += ".exe";
        }
        if(IOHelper.exists(jstackPath)) {
            return jstackPath;
        }

        // jre?????????????????????jdk
        String findJdkPath = javahome + "../";
        return findJstackPath(findJdkPath, isWin);      
    }
}
