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

package com.creditease.agent.helpers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import com.creditease.uav.helpers.network.HostNewworkInfo;

public class NetworkHelper {

    private static final String defaultLocalhostIp = "127.0.0.1";

    private static final String IPREGEX = "((25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d)))\\.){3}(25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d)))";

    private NetworkHelper() {

    }

    /**
     * get the ip by dns
     * 
     * @param dnsName
     * @return
     */
    public static String getIPByDNS(String dnsName) {

        InetAddress address = null;
        try {
            address = InetAddress.getByName(dnsName);
        }
        catch (UnknownHostException e) {
            return null;
        }
        return address.getHostAddress().toString();
    }

    /**
     * getHosts
     * 
     * @return
     */
    public static String[] getHosts() {

        String[] hosts = new String[2];

        hosts[0] = getHostName();
        hosts[1] = getLocalIP();

        return hosts;
    }

    /**
     * getHostName
     * 
     * @return
     */
    public static String getHostName() {

        try {
            java.net.InetAddress localMachine = java.net.InetAddress.getLocalHost();
            return localMachine.getHostName();
        }
        catch (UnknownHostException e) {
            // ignore
        }
        return "UNKOWN_HOST";
    }

    /**
     * get ip by index
     * 
     * @param index
     * @return
     */
    public static String getLocalIP(int index) {

        HostNewworkInfo hni = new HostNewworkInfo();

        try {
            return hni.getIPs().get(index).getHostAddress();
        }
        catch (Exception e) {
            return defaultLocalhostIp;
        }
    }

    public static String getLocalIP(String cardName) {

        HostNewworkInfo hni = new HostNewworkInfo();

        try {
            return hni.getIPs(cardName).get(0).getHostAddress();
        }
        catch (Exception e) {
            return getLocalIP(0);
        }
    }

    public static String getNetCardName(String ip) {

        HostNewworkInfo hni = new HostNewworkInfo();

        return hni.getNetCardName(ip);
    }

    /**
     * getLocalIP
     * 
     * @return
     */
    public static String getLocalIP() {

        String ip = null;

        String index = System.getProperty("NetCardIndex");

        if (!StringHelper.isEmpty(index)) {
            try {
                int i = Integer.parseInt(index);

                ip = getLocalIP(i);

                if (!StringHelper.isEmpty(ip) && (!ip.equals(defaultLocalhostIp))) {
                    return ip;
                }

            }
            catch (NumberFormatException e) {
                // ignore
            }
        }

        String name = System.getProperty("NetCardName");

        if (!StringHelper.isEmpty(name)) {

            ip = getLocalIP(name);

            if (!StringHelper.isEmpty(ip) && (!ip.equals(defaultLocalhostIp))) {
                return ip;
            }
        }

        return getLocalIP(0);
    }

    /*
     * Try command line to get IP address
     */
    public static String getLocalIPByCL() {

        if (JVMToolHelper.isWindows()) {
            return defaultLocalhostIp;
        }

        String ip = null;

        String[] cmd = null;

        Pattern pattern = Pattern.compile(IPREGEX);

        String index = System.getProperty("NetCardIndex");

        try {

            if (!StringHelper.isEmpty(index)) {
                int i = Integer.parseInt(index);
                cmd = new String[] { "/bin/bash", "-c",
                        "ip route list|grep -Po 'src \\K[\\d.]+'|awk 'NR==" + (i + 1) + "'" };

                ip = RuntimeHelper.exec(5000, cmd).trim();

                if (!StringHelper.isEmpty(ip) && pattern.matcher(ip).matches() && !defaultLocalhostIp.equals(ip)) {
                    return ip;
                }
            }

            String name = System.getProperty("NetCardName");

            if (!StringHelper.isEmpty(name)) {
                cmd = new String[] { "/bin/bash", "-c", "ip route list|grep " + name + "|grep -Po 'src \\K[\\d.]+'" };

                ip = RuntimeHelper.exec(5000, cmd).trim();

                if (!StringHelper.isEmpty(ip) && pattern.matcher(ip).matches() && !defaultLocalhostIp.equals(ip)) {
                    return ip;
                }
            }
        }
        catch (Exception e) {
            // ignore
        }

        return defaultLocalhostIp;
    }

    public static boolean isIPV4(String ip) {

        if (ip == null || ip.length() == 0)
            return false;
        String[] array = ip.split("\\.");
        if (array.length != 4)
            return false;
        for (String str : array) {
            try {
                Integer.valueOf(str);
            }
            catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    public static List<String> getNetworkSectionIPs(String startIP, String endIP) {

        if (!isIPV4(startIP) || !isIPV4(endIP))
            return Collections.emptyList();
        ;

        List<String> iplist = new ArrayList<String>();

        if (startIP.equalsIgnoreCase(endIP)) {
            iplist.add(startIP);
        }
        else {

            String pre = startIP.substring(0, startIP.lastIndexOf(".") + 1);
            int first = Integer.parseInt(startIP.split("\\.")[3]);
            int last = Integer.parseInt(endIP.split("\\.")[3]);
            if (first > last) {
                return Collections.emptyList();
            }

            for (int i = first; i <= last; i++) {
                String ipStr = pre + i;
                iplist.add(ipStr);
            }
        }

        return iplist;
    }

    public static boolean isIPReachable(String ipStr, int timeout) {

        try {
            InetAddress inetAddress = InetAddress.getByName(ipStr);

            if (inetAddress.isReachable(timeout)) {
                return true;
            }
        }
        catch (Exception e) {
            // ignore
        }

        return false;
    }

    /**
     * get host MAC address
     * 
     * @return
     */
    public static String getMACAddress() {

        String ipIndex = System.getProperty("NetCardIndex");

        if (!StringHelper.isEmpty(ipIndex)) {
            try {
                int i = Integer.parseInt(ipIndex);
                return getMACAddress(i);
            }
            catch (NumberFormatException e) {
                // ignore
            }
        }

        String name = System.getProperty("NetCardName");

        if (!StringHelper.isEmpty(name)) {

            return getMACAddress(name);
        }

        return getMACAddress(0);

    }

    public static String getMACAddress(int index) {

        HostNewworkInfo hni = new HostNewworkInfo();

        try {
            NetworkInterface ni = NetworkInterface.getByInetAddress(hni.getIPs().get(index));
            return hni.getMacAddressAsString(ni);
        }
        catch (Exception e) {
            return "UNKNOWN";
        }

    }

    public static String getMACAddress(String cardName) {

        HostNewworkInfo hni = new HostNewworkInfo();

        try {
            NetworkInterface ni = NetworkInterface.getByInetAddress(hni.getIPs(cardName).get(0));
            return hni.getMacAddressAsString(ni);
        }
        catch (Exception e) {
            return "UNKNOWN";
        }

    }

    /**
     * ????????????DNS????????????
     * 
     * @return
     */
    public static boolean isNetworkOver() {

        try {
            return !ping("114.114.114.114");
        }
        catch (Exception e) {
            return true;
        }
    }

    /**
     * ???????????????ping???
     * 
     * @param ipAddress
     * @return
     * @throws Exception
     */
    public static boolean ping(String ipAddress) throws Exception {

        String line = null;
        try {
            Process pro = Runtime.getRuntime().exec("ping " + ipAddress);
            BufferedReader buf = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            while ((line = buf.readLine()) != null) {
                if (line.startsWith("PING")) {
                    continue;
                }
                if (line.indexOf("timeout") != -1) {
                    return false;
                }
                return true;
            }
            return false;
        }
        catch (Exception ex) {
            return false;
        }
    }

    public static boolean isLocalIP(String ip) {

        HostNewworkInfo hni = new HostNewworkInfo();

        for (InetAddress localIP : hni.getIPs()) {
            if (localIP.getHostAddress().equals(ip)) {
                return true;
            }
        }
        return false;
    }

    public static List<InetAddress> getAllIP() {

        HostNewworkInfo hni = new HostNewworkInfo();

        return hni.getIPs();
    }

    public static String getNetCardInfo() {

        HostNewworkInfo hni = new HostNewworkInfo();
        return JSONHelper.toString(hni.getNetCardInfo());
    }
}
