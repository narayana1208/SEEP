package uk.ac.imperial.lsds.seep.operator.compose.subquery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.operator.compose.micro.IMicroOperatorConnectable;
import uk.ac.imperial.lsds.seep.operator.compose.multi.SubQueryBuffer;
import uk.ac.imperial.lsds.seep.operator.compose.window.IWindowDefinition;

public class SubQuery {
	
	private int id;
	
	private ISubQueryConnectable parent;
	private Set<IMicroOperatorConnectable> microOperators;
	
	private Map<Integer, SubQueryBuffer> inputQueues;
	private Map<Integer, SubQueryBuffer> outputQueues;
	
	private Map<Integer, IWindowDefinition>  inputWindowDefinitions;
	
	private SubQuery(Set<IMicroOperatorConnectable> microOperators, int id, Map<Integer, IWindowDefinition>  inputWindowDefinitions) {
		this.id = id;
		this.inputQueues = new HashMap<>();
		this.outputQueues = new HashMap<>();
		this.inputWindowDefinitions = inputWindowDefinitions;
	}
	
	public void execute(ExecutorService executorService, int numberThreads, int batchSize) {
		for (int i = 0; i < numberThreads; i++)
			executorService.execute(new SubQueryTask());
	}

	public int getId() {
		return id;
	}

	public void registerInputQueue(Integer upstreamOpId,
			SubQueryBuffer queue) {
		this.inputQueues.put(upstreamOpId, queue);
	}

	public void registerOutputQueue(Integer downstreamOpId,
			SubQueryBuffer queue) {
		this.outputQueues.put(downstreamOpId, queue);
	}

	public void pushData(List<DataTuple> tuples, int streamID) {
		for (DataTuple tuple : tuples)
			pushData(tuple, streamID);
	}

	public void pushData(DataTuple tuple, int streamID) {
		this.inputQueues.get(streamID).add(tuple);
	}
	public static SubQuery newSubQuery (
			Set<IMicroOperatorConnectable> microOperators, int opId, Map<Integer, IWindowDefinition>  inputWindowDefinitions) {
		return new SubQuery(microOperators, opId, inputWindowDefinitions);
	}


	public void setParentSubQueryConnectable(
			ISubQueryConnectable subQueryConnectable) {
		this.parent = subQueryConnectable;
	}

	public void pushDataToAllStreams(DataTuple data) {
		for (Integer streamID : this.inputQueues.keySet())
			pushData(data, streamID);
	};

	
}
