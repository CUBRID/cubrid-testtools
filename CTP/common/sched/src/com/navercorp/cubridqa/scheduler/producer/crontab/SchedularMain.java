/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.

 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice, 
 *     this list of conditions and the following disclaimer.
 * 
 *   * Redistributions in binary form must reproduce the above copyright 
 *     notice, this list of conditions and the following disclaimer in 
 *     the documentation and/or other materials provided with the distribution.
 * 
 *   * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */
package com.navercorp.cubridqa.scheduler.producer.crontab;

import java.util.ArrayList;


import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import com.navercorp.cubridqa.scheduler.producer.Configure;

public class SchedularMain {

	Scheduler scheduler;
	Configure conf;

	public SchedularMain(Configure conf) throws SchedulerException {
		this.conf = conf;
		scheduler = StdSchedulerFactory.getDefaultScheduler();
	}

	public void startup() throws SchedulerException {
		JobDataMap jobmap;
		ArrayList<CUBJobContext> jctxList = conf.findValidJobContexts();
		for (CUBJobContext jctx : jctxList) {
			jobmap = new JobDataMap();
			jobmap.put("jctx", jctx);
			JobDetail job = JobBuilder.newJob(CUBJob.class).withIdentity(jctx.getJobId()).setJobData(jobmap).build();
			CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(jctx.getJobId() + "_trigger").withSchedule(CronScheduleBuilder.cronSchedule(jctx.getCrontab())).build();
			scheduler.scheduleJob(job, trigger);
		}
		scheduler.start();
		System.out.println("[Schedular] START");
	}

	public void shutdown() throws SchedulerException {
		scheduler.shutdown();
		System.out.println("[Schedular] STOP");
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			Configure conf = new Configure();
			SchedularMain m = new SchedularMain(conf);
			m.startup();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}