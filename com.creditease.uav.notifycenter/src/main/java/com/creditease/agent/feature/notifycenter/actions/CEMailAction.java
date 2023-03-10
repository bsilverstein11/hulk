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

package com.creditease.agent.feature.notifycenter.actions;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQObjectMessage;

import com.creditease.agent.helpers.IOHelper;
import com.creditease.agent.helpers.StringHelper;
import com.creditease.agent.monitor.api.NotificationEvent;
import com.creditease.agent.spi.IActionEngine;
import com.creditease.mspl.domain.BizProcess;
import com.creditease.mspl.event.vo.EmailEntity;
import com.creditease.mspl.event.vo.EmailEvent;

/**
 * 
 * @author pengfei
 * @since 20160520
 *
 */
public class CEMailAction extends AbstractMailAction {

    private static List<String> nameList = new LinkedList<String>();

    static {
        nameList.add("ip");
        nameList.add("host");
        nameList.add("component");
        nameList.add("nodename");
        nameList.add("feature");
        nameList.add("nodeuuid");
    }

    // default value, read from configuration
    private String userName = "";
    private String password = "";
    private String brokerURL = "tcp://127.0.0.1:61616";

    private String queueName = "Mail.Receive.queue";

    private String activeID = "ACTIVE-20150429-00001";
    private String systemSign = "1";

    public CEMailAction(String cName, String feature, IActionEngine engine) {
        super(cName, feature, engine);

        userName = this.getConfigManager().getFeatureConfiguration(this.feature, "nc.notify.mail.cemail.username");
        password = this.getConfigManager().getFeatureConfiguration(this.feature, "nc.notify.mail.cemail.password");
        brokerURL = this.getConfigManager().getFeatureConfiguration(this.feature, "nc.notify.mail.cemail.brokerurl");

        activeID = this.getConfigManager().getFeatureConfiguration(this.feature, "nc.notify.mail.cemail.activeid");
        systemSign = this.getConfigManager().getFeatureConfiguration(this.feature, "nc.notify.mail.cemail.systemsign");
    }

    /**
     * ????????????
     * 
     * @param notifyEvent
     * @return EmailEvent
     * @throws Exception
     */
    private EmailEvent buildEmailEvent(NotificationEvent notifyEvent, String title, String mailTemplatePath)
            throws Exception {

        List<EmailEntity> list = new ArrayList<EmailEntity>();

        EmailEntity emailEntity = new EmailEntity();

        /**
         * ??????notifyEvent??????????????????????????????????????????????????????????????????
         */
        String address = notifyEvent.getArg(cName);

        if (StringHelper.isEmpty(address)) {
            if (log.isTraceEnable()) {
                log.warn(this, "Send Mail FAIL as no any email addresses");
            }
            return null;
        }
        emailEntity.setToAddress(address);
        String html = IOHelper.readTxtFile(mailTemplatePath, "utf-8");

        if (StringHelper.isEmpty(html)) {
            log.err(this, "Send Mail FAIL as mail template is empty");
            return null;
        }

        /** ???????????? */
        html = buildMailBody(html, notifyEvent);
        /** ?????? */
        emailEntity.setSubject(title);
        /** ?????? */
        emailEntity.setContent(html);
        /** ???????????? */
        list.add(emailEntity);

        /** ???????????? */
        EmailEvent emailEvent = new EmailEvent();
        BizProcess bizProcess = new BizProcess();
        bizProcess.setDealCode("mspl_ltn_0107");
        emailEvent.setBizProcess(bizProcess);
        emailEvent.setUserList(list);
        emailEvent.setActivityId(activeID);
        emailEvent.setChannelCode("channel 001");
        emailEvent.setMailType("?????????22");
        emailEvent.setSystemSign(systemSign);
        return emailEvent;
    }

    @Override
    public boolean sendMail(String title, String mailTemplatePath, NotificationEvent notifyEvent) {

        if (log.isDebugEnable()) {
            log.debug(this, "Send Mail START: event=" + notifyEvent.toJSONString());
        }

        // ConnectionFactory ??????????????????JMS ??????????????????
        ActiveMQConnectionFactory connectionFactory;
        // Connection ???JMS ????????????JMS Provider ?????????
        Connection connection = null;
        // Session??? ????????????????????????????????????
        Session session = null;
        // Destination ?????????????????????;??????????????????.
        Queue destination;
        // MessageProducer??????????????????
        javax.jms.MessageProducer producer = null;
        // TextMessage message;
        // ??????ConnectionFactory???????????????????????????ActiveMq?????????jar
        connectionFactory = new ActiveMQConnectionFactory(userName, password, brokerURL);
        try {
            // ?????????????????????????????????
            connection = connectionFactory.createConnection();
            // ??????
            connection.start();
            // ??????????????????
            session = connection.createSession(Boolean.TRUE, Session.AUTO_ACKNOWLEDGE);
            // ??????session???????????????xingbo.xu-queue?????????????????????queue????????????ActiveMq???console??????
            destination = session.createQueue(queueName);
            // ????????????????????????????????????
            producer = session.createProducer(destination);
            // ????????????????????????????????????????????????????????????
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            // ?????????????????????????????????????????????????????????????????????
            EmailEvent emailEvent = buildEmailEvent(notifyEvent, title, mailTemplatePath);

            if (emailEvent == null) {
                return false;
            }

            // ?????????
            ActiveMQObjectMessage message = (ActiveMQObjectMessage) session.createObjectMessage();
            message.setObject(emailEvent);

            // ???????????????????????????
            producer.send(message);

            session.commit();

            return true;
        }
        catch (Exception e) {
            log.err(this, "Send Mail FAIL.", e);

        }
        finally {

            try {
                if (producer != null) {
                    producer.close();
                }
            }
            catch (Throwable e) {
                // ignore
            }

            try {
                if (session != null) {
                    session.close();
                }
            }
            catch (Throwable e) {
                // ignore
            }

            try {
                if (null != connection)
                    connection.close();
            }
            catch (Throwable e) {
                // ignore
            }
        }
        return false;
    }

}
