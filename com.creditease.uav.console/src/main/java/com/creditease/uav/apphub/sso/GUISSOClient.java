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
package com.creditease.uav.apphub.sso;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import com.creditease.agent.helpers.JSONHelper;
import com.creditease.agent.log.SystemLogger;
import com.creditease.agent.log.api.ISystemLogger;
import com.creditease.uav.cache.api.CacheManager;
import com.creditease.uav.cache.api.CacheManagerFactory;

public abstract class GUISSOClient {

    protected static final List<Map<String, String>> systemUser = new ArrayList<Map<String, String>>();
    protected static char[] hex = "0123456789abcdef".toCharArray();
    protected static CacheManager cm = null;
    protected static MessageDigest md = null;
    protected ISystemLogger logger = SystemLogger.getLogger(GUISSOLdapClient.class);

    protected GUISSOClient() {

    }

    public GUISSOClient(HttpServletRequest request) {

        initMessageMD5();
        initSystemUser(request);

    }

    public Map<String, String> getUserByLogin(String loginId, String loginPwd) {

        Map<String, String> userInfo = new HashMap<String, String>();

        userInfo = getUserBySystem(loginId, loginPwd);

        if (userInfo.isEmpty()) {
            userInfo = getUserByLoginImpl(loginId, loginPwd);
        }

        return userInfo;
    }

    /**
     * return Map key schema :
     * 
     * "loginId":loginId
     * 
     * "groupId":???????????????????????????
     * 
     * "emailList":????????????????????????
     */
    protected abstract Map<String, String> getUserByLoginImpl(String loginId, String loginPwd);

    /**
     * return List<Map> key schema :
     * 
     * "name":????????????
     * 
     * "email":????????????(????????????)
     * 
     * "groupId":???????????????????????????
     * 
     * "emailList":????????????????????????
     */
    public abstract List<Map<String, String>> getUserByQuery(String email);

    /**
     * return Map key schema :
     * 
     * "name":????????????(String??????)
     * 
     * "email":????????????????????????(String??????)
     * 
     * "groupIdFiltered":??????????????????????????????????????????(Set<String>??????)
     * 
     * "emailList_Filtered":??????????????????????????????????????? (Set<String>??????)
     * 
     * "userInfo":??????????????????????????????name???email???groupID???emailList???(List<Map<String,String>>??????)
     */
    public abstract Map<String, Object> getEmailListByQuery(String email);

    // ======================================init begin========================================
    private void initMessageMD5() {

        try {
            if (null == md) {
                md = MessageDigest.getInstance("MD5");
            }
        }
        catch (NoSuchAlgorithmException e) {
            loggerError("initMessageMD5", "", e);
        }

    }

    private void initSystemUser(HttpServletRequest request) {

        if (systemUser.isEmpty()) {

            String adminLoginid = request.getServletContext().getInitParameter("uav.apphub.sso.admin.loginid");
            String adminPassword = request.getServletContext().getInitParameter("uav.apphub.sso.admin.password");
            Map<String, String> adminInfo = new HashMap<String, String>();
            adminInfo.put("loginId", adminLoginid);
            adminInfo.put("loginPwd", adminPassword);
            adminInfo.put("groupId", "uav_admin");
            adminInfo.put("emailList", "UAV.ADMIN.EMAIL.LIST");
            systemUser.add(adminInfo);

            String guestLoginid = request.getServletContext().getInitParameter("uav.apphub.sso.guest.loginid");
            String guestPassword = request.getServletContext().getInitParameter("uav.apphub.sso.guest.password");
            Map<String, String> guestInfo = new HashMap<String, String>();
            guestInfo.put("loginId", guestLoginid);
            guestInfo.put("loginPwd", guestPassword);
            guestInfo.put("groupId", "uav_guest");
            guestInfo.put("emailList", "UAV.GUEST.EMAIL.LIST");
            systemUser.add(guestInfo);
        }

    }

    private Map<String, String> getUserBySystem(String loginId, String loginPwd) {

        Map<String, String> result = new HashMap<String, String>();

        for (Map<String, String> user : systemUser) {
            if (user.get("loginId").equals(loginId) && user.get("loginPwd").equals(loginPwd)) {
                result.putAll(user);
                break;
            }
        }

        return result;
    }

    protected void loggerInfo(String title, String action, String result, String msg) {

        logger.info(this, title + "[" + action + "]" + result + " " + msg);
    }

    protected void loggerError(String title, String msg, Exception e) {

        logger.err(this, title + "[??????] " + msg, e.getMessage(), e);
    }

}
