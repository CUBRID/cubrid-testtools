/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.
 */
package com.navercorp.cubridqa.scheduler.bisect;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import com.navercorp.cubridqa.scheduler.common.ActiveMQFactory;
import com.navercorp.cubridqa.scheduler.common.Log;
import com.navercorp.cubridqa.scheduler.consumer.Configure;

public class BisectWorkerMain {
    
    public static void main(String[] args) throws Exception {
        Configure conf = new Configure();
        
        // Get ActiveMQ connection details
        String user = conf.getProperty("activemq_user");
        String passwd = conf.getProperty("activemq_pwd");
        String url = conf.getProperty("activemq_url");
        
        // Create ActiveMQ connection
        ActiveMQFactory mq = new ActiveMQFactory(user, passwd, url);
        Connection conn = mq.getConn();
        conn.start();
        
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        
        // Listen to bisect.request queue
        Destination destination = session.createQueue("bisect.request");
        MessageConsumer consumer = session.createConsumer(destination);
        
        // Create and set message listener
        BisectWorker worker = new BisectWorker(conf);
        consumer.setMessageListener(worker);
        
        Log.info("Bisect Worker started, listening on bisect.request queue");
        
        // Keep the worker running
        while (true) {
            Thread.sleep(1000);
        }
    }
}