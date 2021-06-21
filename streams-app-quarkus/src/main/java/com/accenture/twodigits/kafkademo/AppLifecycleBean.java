package com.accenture.twodigits.kafkademo;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class AppLifecycleBean {
    
	void onStart(@Observes StartupEvent ev) {
		System.out.println("The application is starting ...");
		// Get JVM's thread system bean
//		RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
//
//		// Get start time
//		long startTime = bean.getStartTime();
//		long now = new Date().getTime();
//		
//		long duration = now - startTime;
//
//		// print Starttime
//		System.out.println("Application started in " + duration/1000f + " Seconds");
	}

    void onStop(@Observes ShutdownEvent ev) {               
        System.out.println("The application is stopping...");
    }
}
