package uk.ac.imperial.lsds.streamsql.op.gpu;

import java.util.List;

import com.amd.aparapi.ProfileInfo;

public class KernelStatistics {

	private long runs;
	/* Time counters */
	private long Tr;
	private long Tw;
	private long Tx;
	/* Byte counters */
	private long Br;
	private long Bw;

	private static final double _1GB = 1073741824.;

	private String name = "default"; /* Kernel name */

	public KernelStatistics() {
		reset();
	}

	private void reset() {
		this.runs = 0L;
		this.Tr = this.Tw = this.Tx = 0L;
		this.Br = this.Bw = 0L;
	}

	public void collect (List<ProfileInfo> info, int input, int output) {
		long dt;
		String type;
		runs ++;
		Br += input;
		Bw += output;
		for (ProfileInfo p : info) {
			dt = (p.getEnd() - p.getStart()) / 1000; /* microseconds */
			type = String.format("%1s", p.getType());
			if (type.equals("R")) {
				Tr += dt;
			} else if (type.equals("X")) {
				Tx += dt;
			} else if (type.equals("W")) {
				Tw += dt;
			} else {
				throw new IllegalArgumentException(
						"error: unknown measurement type");
			}
		}
	}
	
	private double rate (long bytes, long usecs) {
		double Dt = usecs / 1000000.;
		double Gb = bytes / _1GB;
		return (Gb / Dt);
	}
	
	private double time (long usecs) {
		return (double) usecs / (double) runs;
	}
	
	public void print () {
		System.out.println(String.format(
		"[DBG] [GPU] %10s R %5.1f usec/call %10.3f GB/s W %5.1f usec/call %10.3f GB/s X %5.1f usec/call %10.3f GB/s", 
		name, time (Tr), rate(Br, Tr), time (Tw), rate(Bw, Tw), time (Tx), rate(Br + Bw, Tx)));
	}
}