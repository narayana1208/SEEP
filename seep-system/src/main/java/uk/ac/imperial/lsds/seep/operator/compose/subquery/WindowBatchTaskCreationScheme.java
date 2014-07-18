package uk.ac.imperial.lsds.seep.operator.compose.subquery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.GLOBALS;
import uk.ac.imperial.lsds.seep.operator.compose.multi.SubQueryBuffer;
import uk.ac.imperial.lsds.seep.operator.compose.window.IPeriodicWindowBatch;
import uk.ac.imperial.lsds.seep.operator.compose.window.IWindowBatch;
import uk.ac.imperial.lsds.seep.operator.compose.window.IWindowDefinition;
import uk.ac.imperial.lsds.seep.operator.compose.window.PeriodicWindowBatch;

public class WindowBatchTaskCreationScheme implements
		SubQueryTaskCreationScheme {

	private final Logger LOG = LoggerFactory.getLogger(WindowBatchTaskCreationScheme.class);

	private static final int SUB_QUERY_WINDOW_BATCH_COUNT = Integer.valueOf(GLOBALS.valueFor("subQueryWindowBatchCount"));

	private Iterator<SubQueryTask> iter;
	
	private long logicalOrderID = 0;

	private ISubQueryConnectable subQueryConnectable;

	private Map<Integer, Long> nextToProcessPointers;
	private Map<Integer, Integer> waitForClearanceOfIndex;
	
	public WindowBatchTaskCreationScheme(ISubQueryConnectable subQueryConnectable){
		this.subQueryConnectable = subQueryConnectable;
		this.nextToProcessPointers = new HashMap<>();
		this.waitForClearanceOfIndex = new HashMap<>();
		for (Integer streamID : this.subQueryConnectable.getLocalUpstreamBuffers().keySet()) {
			this.nextToProcessPointers.put(streamID, -1l);
			this.waitForClearanceOfIndex.put(streamID, -1);
		}
	}
	
	@Override
	public boolean hasNext() {
		return iter.hasNext();
	}

	@Override
	public SubQueryTask next() {
		return iter.next();
	}

	@Override
	public void remove() {
		throw new IllegalArgumentException("");
	}
	
	@Override
	public void createTasks() {
		Map<Integer, IWindowDefinition> winDefs = subQueryConnectable.getSubQuery().getWindowDefinitions();
		
		assert(SUB_QUERY_WINDOW_BATCH_COUNT > 0);
		
		List<SubQueryTask> tasks = new ArrayList<SubQueryTask>();

		// if we have data, create the tasks
		boolean sufficientData = sufficientDataForWindowBatch();
		while (sufficientData) {
			Map<Integer, IWindowBatch> windowBatches = new HashMap<>();
			Map<SubQueryBuffer, Integer> freeUpToIndices = new HashMap<>();
			for (Integer streamID : subQueryConnectable.getLocalUpstreamBuffers().keySet()) {
				
				SubQueryBuffer buffer = subQueryConnectable.getLocalUpstreamBuffers().get(streamID);
				if (!freeUpToIndices.containsKey(buffer))
					freeUpToIndices.put(buffer, Integer.MAX_VALUE);
	
				IWindowDefinition windowDef = winDefs.get(streamID); 
				long nextToProcessPointer = nextToProcessPointers.get(streamID);
				switch (windowDef.getWindowType()) {
				
				case ROW_BASED:
					// define periodic window batch
					int start = (int) nextToProcessPointer;
					int end = buffer.normIndex(start + (int) windowDef.getSlide() * (SUB_QUERY_WINDOW_BATCH_COUNT-1) + (int)windowDef.getSize());
					IPeriodicWindowBatch windowBatch = new PeriodicWindowBatch(windowDef, buffer, start, end);
					windowBatches.put(streamID, windowBatch);
					// update progress
					nextToProcessPointers.put(streamID, (long)buffer.normIndex(start + (int)windowDef.getSlide() * SUB_QUERY_WINDOW_BATCH_COUNT));
					freeUpToIndices.put(buffer, Math.min(freeUpToIndices.get(buffer), buffer.normIndex(start + (int)windowDef.getSlide() * SUB_QUERY_WINDOW_BATCH_COUNT)));
					break;
	
				case RANGE_BASED:
					/*
					 * if we have not yet processed any tuple, we take the first in the buffer. Here,
					 * we can be sure that the buffer is not empty since we have sufficient data for the
					 * window batch
					 */
					if (nextToProcessPointer == -1)
						nextToProcessPointer = buffer.get(buffer.getStartIndex()).getPayload().timestamp;
					
					long startTimeForWindowBatch = nextToProcessPointer;
					long endTimeForWindowBatch = startTimeForWindowBatch + windowDef.getSlide() * (SUB_QUERY_WINDOW_BATCH_COUNT-1) + windowDef.getSize();
					// determine first index larger or equal than start timestamp
					start = buffer.getStartIndex();
					while (buffer.get(start).getPayload().timestamp < startTimeForWindowBatch)
						start++;
					
					// determine last index smaller or equal than end timestamp
					end = start;
					while (buffer.get(end).getPayload().timestamp <= endTimeForWindowBatch)
						end++;

					// determine last index smaller or equal than the start for the next window batch 
					long timestampForNextWindowBatch = startTimeForWindowBatch + windowDef.getSlide() * SUB_QUERY_WINDOW_BATCH_COUNT;
					int indexForNextWindowBatch = start;
					while (buffer.get(indexForNextWindowBatch).getPayload().timestamp <= timestampForNextWindowBatch)
						indexForNextWindowBatch++;

					// define periodic window batch
					windowBatch = new PeriodicWindowBatch(windowDef, buffer, buffer.normIndex(start), buffer.normIndex(end), startTimeForWindowBatch, endTimeForWindowBatch);
					windowBatches.put(streamID, windowBatch);

					// update progress
					nextToProcessPointers.put(streamID, timestampForNextWindowBatch);
					freeUpToIndices.put(buffer, Math.min(freeUpToIndices.get(buffer), indexForNextWindowBatch));
					break;
	
				default:
					LOG.error("Unknown window definition: {}", windowDef.getWindowType().toString());
					break;
				}
			}
			SubQueryTask task = new SubQueryTask(subQueryConnectable, windowBatches, this.logicalOrderID, freeUpToIndices);
			this.logicalOrderID++;
			tasks.add(task);
			sufficientData = sufficientDataForWindowBatch();
		}
				
		this.iter = tasks.iterator();
	}
	
	private boolean sufficientDataForWindowBatch() {

		boolean sufficientData = true;
		for (Integer streamID : subQueryConnectable.getLocalUpstreamBuffers().keySet()) {
			sufficientData &= sufficientDataForStream(streamID, subQueryConnectable.getLocalUpstreamBuffers().get(streamID));
			if (!sufficientData)
				break;
		}
		
		return sufficientData;
	}


	private boolean sufficientDataForStream(Integer streamID, SubQueryBuffer buffer){

		
		/*
		 * Note that nextToProcessPointer may refer to an index in the 
		 * buffer (row based window) or a timestamp (range based window)
		 */
		boolean sufficientData = true;
		IWindowDefinition windowDef = subQueryConnectable.getSubQuery().getWindowDefinitions().get(streamID); 
		long nextToProcessPointer = nextToProcessPointers.get(streamID);
		switch (windowDef.getWindowType()) {
		case ROW_BASED:
			// if we have not yet processed any tuple, we take the first in the buffer
			if (nextToProcessPointer == -1) {
				if (buffer.size() == 0) {
					sufficientData = false;
					break;
				}
				nextToProcessPointer = buffer.getStartIndex();
			}

			// pointing into an area that is not yet filled?
			if (!buffer.validIndex((int) nextToProcessPointer)){
				sufficientData = false;
				break;
			}
			
				
			
			// begin from the start?
			if (buffer.getStartIndex() == (int) nextToProcessPointer) {
				sufficientData &= (buffer.size() >= (int)windowDef.getSlide() * (SUB_QUERY_WINDOW_BATCH_COUNT-1) + (int)windowDef.getSize());
				break;
			}
			
			// consider only some part of the buffer
			int unprocessedTuples = (buffer.getEndIndex() + buffer.capacity() - (int) nextToProcessPointer) % buffer.capacity();
			// is that enough data given the window definition?
			sufficientData &= (unprocessedTuples >= (int)windowDef.getSlide() * (SUB_QUERY_WINDOW_BATCH_COUNT-1) + (int)windowDef.getSize());
			break;
		case RANGE_BASED:
			// if we have not yet processed any tuple, we take the first in the buffer
			if (nextToProcessPointer == -1) {
				if (buffer.size() == 0) {
					sufficientData = false;
					break;
				}
				nextToProcessPointer = buffer.get(buffer.getStartIndex()).getPayload().timestamp;
			}
			long endTimeForWindowBatch = nextToProcessPointer + windowDef.getSlide() * (SUB_QUERY_WINDOW_BATCH_COUNT-1) + windowDef.getSize();
			// check whether end time for window batch has passed already
			sufficientData &= (endTimeForWindowBatch < buffer.get(buffer.getEndIndex()).getPayload().timestamp);
			break;

		default:
			LOG.error("Unknown window definition: {}", windowDef.getWindowType().toString());
			break;
		}
		return sufficientData;
	}

	
}
