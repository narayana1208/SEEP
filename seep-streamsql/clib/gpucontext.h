#ifndef __GPU_CONTEXT_H_
#define __GPU_CONTEXT_H_

#include "utils.h"

#include "inputbuffer.h"
#include "outputbuffer.h"

#include <CL/cl.h>

typedef struct gpu_kernel_input {
	int count;
	inputBufferP inputs [MAX__INPUTS];
} gpu_kernel_input_t;

typedef struct gpu_kernel_output {
	int count;
	outputBufferP outputs [MAX_OUTPUTS];
} gpu_kernel_output_t;

typedef struct a_kernel *aKernelP;
typedef struct a_kernel {
	cl_kernel kernel [2];
} a_kernel_t;

typedef struct gpu_kernel {
	int count;
	aKernelP kernels [MAX_KERNELS]; /* Every query has one or more kernels */
} gpu_kernel_t;

typedef struct gpu_context *gpuContextP;
typedef struct gpu_context {
	cl_device_id device;
	cl_context context;
	cl_program program;
	gpu_kernel_t kernel;
	gpu_kernel_input_t kernelInput;
	gpu_kernel_output_t kernelOutput;
	cl_command_queue queue [2];
	int scheduled;
	cl_event read_event;
	cl_event write_event;
} gpu_context_t;

gpuContextP gpu_context (cl_device_id, cl_context, cl_program, int, int, int);

void gpu_context_free (gpuContextP);

void gpu_context_setInput (gpuContextP, int, int);

void gpu_context_setOutput (gpuContextP, int, int, int);

void gpu_context_setKernel (gpuContextP, int,
		const char *, void (*callback)(cl_kernel, gpuContextP, int *), int *);

void gpu_context_waitForReadEvent (gpuContextP);

void gpu_context_waitForWriteEvent (gpuContextP);

void gpu_context_flush (gpuContextP);

void gpu_context_finish (gpuContextP);

void gpu_context_writeInput (gpuContextP, void *);

void gpu_context_submitTask (gpuContextP, size_t, size_t);

void gpu_context_readOutput (gpuContextP, void *);

#endif /* __GPU_CONTEXT_H_ */
