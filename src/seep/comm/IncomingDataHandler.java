package seep.comm;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.util.Map;

import seep.infrastructure.NodeManager;
import seep.infrastructure.monitor.MetricsReader;
import seep.runtimeengine.CoreRE;

/**
* IncomingDataHandler. This is in charge of managing incoming data connections and associate a thread to them
*/

public class IncomingDataHandler implements Runnable{

	//private Operator owner;
	private CoreRE owner;
	private int connPort;
	private boolean goOn;
	private Map<String, Integer> idxMapper;

	public int getConnPort(){
		return connPort;
	}

	public void setConnPort(int connPort){
		this.connPort = connPort;
	}

	public IncomingDataHandler(CoreRE owner, int connPort, Map<String, Integer> idxMapper){
		this.owner = owner;
		this.connPort = connPort;
		//this.selector = initSelector();
		this.goOn = true;
		this.idxMapper = idxMapper;
	}

	public void run(){
		ServerSocket incDataServerSocket = null;
		try{
			//Establish listening port
			incDataServerSocket = new ServerSocket(connPort);
			incDataServerSocket.setReuseAddress(true);
			NodeManager.nLogger.info("-> IncomingDataHandler listening in port: "+connPort);
			//Upstream id
			int uid = 0;
			while(goOn){
				Thread newConn = new Thread(new IncomingDataHandlerWorker(uid, incDataServerSocket.accept(), owner, idxMapper));
				newConn.start();
				MetricsReader.numberIncomingDataHandlerWorkers.inc();
				uid++;
			}
			incDataServerSocket.close();
		}
		catch(BindException be){
			NodeManager.nLogger.severe("-> BIND EXC IO Error "+be.getMessage());
			NodeManager.nLogger.severe("-> Was trying to connect to: "+connPort);
			be.printStackTrace();
		}
		catch(IOException io){
			NodeManager.nLogger.severe("-> IncomingDataHandler. While listening incoming conns "+io.getMessage());
			io.printStackTrace();
		}
	}
}