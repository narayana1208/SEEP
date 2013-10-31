/*******************************************************************************
 * Copyright (c) 2013 Imperial College London.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Raul Castro Fernandez - initial design and implementation
 ******************************************************************************/
package uk.ac.imperial.lsds.seep.infrastructure.monitor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.P;
import uk.ac.imperial.lsds.seep.comm.serialization.MetricsTuple;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

/**
* Monitor. This class implements runnable and is in charge of retrieving information from the system. There is a monitor in each node that is being used by the system.
*/

public class Monitor implements Runnable{
	
	final private Logger LOG = LoggerFactory.getLogger(Monitor.class); 

	private int nodeId;
	private static Kryo k = null;
	private static Output output = null;
	
	private boolean listen = true;
	
	private Kryo initializeKryo(){
		k = new Kryo();
		k.register(MetricsTuple.class);
		return k;
	}
	
	public void setNodeId(int opId) {
		this.nodeId = opId;
	}
	
	public void stopMonitor(){
		listen = false;
	}
	
	public static void send666(){
		MetricsTuple mt = new MetricsTuple();
		mt.setOpId(-666);
		k.writeObject(output, mt);
		output.flush();
	}

	private void sendMonitorInfo() throws IOException{
		//Pick metrics
		long inputQueueEvents = MetricsReader.eventsInputQueue.getCount();
		long numberIncomingdataHandlerWorkers = MetricsReader.numberIncomingDataHandlerWorkers.getCount();
		MetricsTuple mt = new MetricsTuple();
		mt.setOpId(nodeId);
		mt.setInputQueueEvents(inputQueueEvents);
		mt.setNumberIncomingDataHandlerWorkers(numberIncomingdataHandlerWorkers);
		k.writeObject(output, mt);
		output.flush();
	}


	public void run(){
		//Initialize kryo to send the serialized data
		initializeKryo();
		
		initializeLocalReporter();
		
		try{
			//Establish connection with the monitor manager.
			InetAddress addrMon = InetAddress.getByName(P.valueFor("mainAddr"));
			int portMon = Integer.parseInt(P.valueFor("monitorManagerPort"));
			LOG.info("MONITOR-> conn ip: {} port: {}", addrMon.toString(), portMon);
			Socket conn = new Socket(addrMon, portMon);
			OutputStream out = conn.getOutputStream();
			output = new Output(out);
			//Monitoring interval
			int sleepInterval = 1000*(Integer.parseInt(P.valueFor("monitorInterval"))-1);
			
			//Runtime monitor loop
			while(listen){
				//Remote info
				Thread.sleep(sleepInterval);
				sendMonitorInfo();
			}
			output.close();
			conn.close();
		}
		catch(IOException io){
			LOG.error("When trying to connect to the MonitorManager: "+io.getMessage());
			io.printStackTrace();
		}
		catch(InterruptedException ie){
			LOG.error("When trying to sleep: "+ie.getMessage());
			ie.printStackTrace();
		}
	}
	
	public void initializeLocalReporter(){
		LocalReporterMonitor lrm = new LocalReporterMonitor();
		Thread t = new Thread(lrm);
		t.start();
	}
}
