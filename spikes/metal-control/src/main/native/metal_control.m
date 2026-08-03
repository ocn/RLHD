#import <Cocoa/Cocoa.h>
#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>
#import <dispatch/dispatch.h>
#import <jni.h>

#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

enum
{
	COUNTER_INIT_ERRORS,
	COUNTER_SHADER_ERRORS,
	COUNTER_PIPELINE_ERRORS,
	COUNTER_SUBMITTED,
	COUNTER_COMPLETED,
	COUNTER_COMMAND_ERRORS,
	COUNTER_PRESENT_REQUESTED,
	COUNTER_NIL_DRAWABLE,
	COUNTER_SKIPPED_SUSPENDED,
	COUNTER_SKIPPED_IN_FLIGHT,
	COUNTER_UI_UPLOAD_BYTES,
	COUNTER_RESIZE_REBUILDS,
	COUNTER_DEVICE_REBUILDS,
	COUNTER_LIVE_NATIVE_OBJECTS,
	COUNTER_HIGH_WATER_NATIVE_OBJECTS,
	COUNTER_MAX_IN_FLIGHT,
	COUNTER_COUNT
};

typedef enum PresentMode
{
	PRESENT_FIFO_LIKE,
	PRESENT_UNLOCKED
} PresentMode;

typedef enum FrameOutcome
{
	OUTCOME_SUBMITTED,
	OUTCOME_SKIPPED_SUSPENDED,
	OUTCOME_SKIPPED_IN_FLIGHT,
	OUTCOME_NIL_DRAWABLE,
	OUTCOME_REJECTED,
	OUTCOME_ERROR
} FrameOutcome;

typedef struct TextureSlot
{
	id<MTLTexture> texture;
	NSUInteger width;
	NSUInteger height;
	bool busy;
} TextureSlot;

typedef struct FrameTiming
{
	uint64_t frame_id;
	NSUInteger width;
	NSUInteger height;
	PresentMode requested_mode;
	PresentMode effective_mode;
	uint64_t ui_generate_ns;
	uint64_t ui_upload_ns;
	uint64_t encode_ns;
	uint64_t submit_ns;
	uint64_t total_ns;
	int slot_index;
} FrameTiming;

typedef struct MetalControlState
{
	pthread_mutex_t mutex;
	pthread_cond_t drained;
	bool accepting;
	bool ready;
	CAMetalLayer *layer;
	id<MTLDevice> device;
	id<MTLCommandQueue> queue;
	id<MTLLibrary> library;
	id<MTLRenderPipelineState> triangle_pipeline;
	id<MTLRenderPipelineState> ui_pipeline;
	id<MTLSamplerState> sampler;
	TextureSlot slots[3];
	NSUInteger inflight;
	PresentMode requested_mode;
	PresentMode effective_mode;
	FILE *log;
	uint64_t counters[COUNTER_COUNT];
} MetalControlState;

static NSString *const SHADER_SOURCE = @
"#include <metal_stdlib>\n"
"using namespace metal;\n"
"struct Out { float4 position [[position]]; float4 color; float2 uv; };\n"
"vertex Out triangle_vertex(uint vid [[vertex_id]], constant float &phase [[buffer(0)]]) {\n"
"  float2 p[3] = { float2(-0.65,-0.55), float2(0.0,0.65), float2(0.65,-0.55) };\n"
"  float2 q = p[vid]; float c=cos(phase), s=sin(phase); q=float2(c*q.x-s*q.y,s*q.x+c*q.y);\n"
"  Out o; o.position=float4(q,0,1); o.color=float4(0.15,0.80,0.25,1); o.uv=float2(0); return o; }\n"
"fragment float4 triangle_fragment(Out in [[stage_in]]) { return in.color; }\n"
"vertex Out ui_vertex(uint vid [[vertex_id]]) {\n"
"  float2 p[6]={float2(-1,-1),float2(1,-1),float2(-1,1),float2(-1,1),float2(1,-1),float2(1,1)};\n"
"  float2 uv[6]={float2(0,1),float2(1,1),float2(0,0),float2(0,0),float2(1,1),float2(1,0)};\n"
"  Out o; o.position=float4(p[vid],0,1); o.color=float4(1); o.uv=uv[vid]; return o; }\n"
"fragment float4 ui_fragment(Out in [[stage_in]], texture2d<float> ui [[texture(0)]], sampler smp [[sampler(0)]]) { return ui.sample(smp,in.uv); }\n";

static uint64_t monotonic_ns(void)
{
	struct timespec value;
	clock_gettime(CLOCK_MONOTONIC, &value);
	return (uint64_t) value.tv_sec * UINT64_C(1000000000) + (uint64_t) value.tv_nsec;
}

static void dispatch_main_sync(dispatch_block_t block)
{
	if ([NSThread isMainThread])
	{
		block();
	}
	else
	{
		dispatch_sync(dispatch_get_main_queue(), block);
	}
}

static void throw_exception(JNIEnv *env, const char *class_name, const char *message)
{
	jclass exception_class = (*env)->FindClass(env, class_name);
	if (exception_class != NULL)
	{
		(*env)->ThrowNew(env, exception_class, message);
	}
}

static MetalControlState *state_from_handle(JNIEnv *env, jlong handle)
{
	if (handle == 0)
	{
		throw_exception(env, "java/lang/IllegalStateException", "Native Metal control state is unavailable.");
		return NULL;
	}
	return (MetalControlState *) (intptr_t) handle;
}

static NSString *string_from_java(JNIEnv *env, jstring value)
{
	if (value == NULL)
	{
		return @"";
	}
	const char *utf = (*env)->GetStringUTFChars(env, value, NULL);
	if (utf == NULL)
	{
		return nil;
	}
	NSString *string = [NSString stringWithUTF8String:utf];
	(*env)->ReleaseStringUTFChars(env, value, utf);
	return string;
}

static PresentMode parse_present_mode(NSString *value)
{
	return [value isEqualToString:@"unlocked"] ? PRESENT_UNLOCKED : PRESENT_FIFO_LIKE;
}

static const char *present_mode_name(PresentMode mode)
{
	return mode == PRESENT_UNLOCKED ? "unlocked" : "fifo-like";
}

static const char *outcome_name(FrameOutcome outcome)
{
	switch (outcome)
	{
		case OUTCOME_SUBMITTED: return "submitted";
		case OUTCOME_SKIPPED_SUSPENDED: return "skipped-suspended";
		case OUTCOME_SKIPPED_IN_FLIGHT: return "skipped-in-flight";
		case OUTCOME_NIL_DRAWABLE: return "nil-drawable";
		case OUTCOME_REJECTED: return "rejected";
		case OUTCOME_ERROR: return "error";
	}
	return "error";
}

static void track_create(MetalControlState *state)
{
	state->counters[COUNTER_LIVE_NATIVE_OBJECTS]++;
	if (state->counters[COUNTER_LIVE_NATIVE_OBJECTS] > state->counters[COUNTER_HIGH_WATER_NATIVE_OBJECTS])
	{
		state->counters[COUNTER_HIGH_WATER_NATIVE_OBJECTS] = state->counters[COUNTER_LIVE_NATIVE_OBJECTS];
	}
}

static void track_release(MetalControlState *state)
{
	if (state->counters[COUNTER_LIVE_NATIVE_OBJECTS] > 0)
	{
		state->counters[COUNTER_LIVE_NATIVE_OBJECTS]--;
	}
}

static void write_counters(FILE *log, const uint64_t *counters)
{
	fprintf(log,
		"{\"init_errors\":%llu,\"shader_errors\":%llu,\"pipeline_errors\":%llu,"
		"\"submitted\":%llu,\"completed\":%llu,\"command_errors\":%llu,"
		"\"present_requested\":%llu,\"nil_drawable\":%llu,\"skipped_suspended\":%llu,"
		"\"skipped_in_flight\":%llu,\"ui_upload_bytes\":%llu,\"resize_rebuilds\":%llu,"
		"\"device_rebuilds\":%llu,\"live_native_objects\":%llu,\"high_water_native_objects\":%llu,"
		"\"max_in_flight\":%llu}",
		(unsigned long long) counters[0], (unsigned long long) counters[1],
		(unsigned long long) counters[2], (unsigned long long) counters[3],
		(unsigned long long) counters[4], (unsigned long long) counters[5],
		(unsigned long long) counters[6], (unsigned long long) counters[7],
		(unsigned long long) counters[8], (unsigned long long) counters[9],
		(unsigned long long) counters[10], (unsigned long long) counters[11],
		(unsigned long long) counters[12], (unsigned long long) counters[13],
		(unsigned long long) counters[14], (unsigned long long) counters[15]);
}

static void log_run_start(MetalControlState *state)
{
	if (state->log == NULL)
	{
		return;
	}
	fprintf(state->log,
		"{\"schema\":\"rlhd.renderer.timing/v1\",\"type\":\"run_start\",\"backend\":\"metal-control\","
		"\"timestamp_ns\":%llu,\"requested_present_mode\":\"%s\",\"effective_present_mode\":\"%s\"}\n",
		(unsigned long long) monotonic_ns(), present_mode_name(state->requested_mode), present_mode_name(state->effective_mode));
	fflush(state->log);
}

static void log_frame(MetalControlState *state, uint64_t frame_id, NSUInteger width, NSUInteger height,
	PresentMode requested, PresentMode effective, FrameOutcome outcome, uint64_t ui_generate_ns, uint64_t ui_upload_ns,
	uint64_t encode_ns, uint64_t submit_ns, uint64_t total_ns, id<MTLCommandBuffer> command_buffer,
	bool present_requested, bool drawable_available, const char *error)
{
	if (state->log == NULL)
	{
		return;
	}
	fprintf(state->log,
		"{\"schema\":\"rlhd.renderer.timing/v1\",\"type\":\"frame\",\"backend\":\"metal-control\","
		"\"frame_id\":%llu,\"resolution\":{\"width\":%lu,\"height\":%lu},"
		"\"requested_present_mode\":\"%s\",\"effective_present_mode\":\"%s\",\"outcome\":\"%s\","
		"\"cpu_ns\":{\"ui_generate\":%llu,\"ui_upload\":%llu,\"encode\":%llu,\"submit\":%llu,\"total\":%llu},",
		(unsigned long long) frame_id, (unsigned long) width, (unsigned long) height,
		present_mode_name(requested), present_mode_name(effective), outcome_name(outcome),
		(unsigned long long) ui_generate_ns, (unsigned long long) ui_upload_ns, (unsigned long long) encode_ns,
		(unsigned long long) submit_ns, (unsigned long long) total_ns);
	if (command_buffer != nil && command_buffer.GPUStartTime > 0.0 && command_buffer.GPUEndTime >= command_buffer.GPUStartTime)
	{
		uint64_t start = (uint64_t) (command_buffer.GPUStartTime * 1000000000.0);
		uint64_t end = (uint64_t) (command_buffer.GPUEndTime * 1000000000.0);
		fprintf(state->log, "\"gpu_ns\":{\"start\":%llu,\"end\":%llu,\"duration\":%llu},",
			(unsigned long long) start, (unsigned long long) end, (unsigned long long) (end - start));
	}
	else
	{
		fprintf(state->log, "\"gpu_ns\":null,");
	}
	fprintf(state->log, "\"present\":{\"requested\":%s,\"drawable_available\":%s},\"counters\":",
		present_requested ? "true" : "false", drawable_available ? "true" : "false");
	write_counters(state->log, state->counters);
	fprintf(state->log, ",\"error\":%s}\n", error == NULL ? "null" : "\"command-buffer-error\"");
	fflush(state->log);
}

static void configure_present_mode_locked(MetalControlState *state, PresentMode requested)
{
	state->requested_mode = requested;
	__block bool supports_unlocked = false;
	dispatch_main_sync(^{
		state->layer.presentsWithTransaction = NO;
		supports_unlocked = [state->layer respondsToSelector:@selector(setDisplaySyncEnabled:)];
		if (requested == PRESENT_UNLOCKED && supports_unlocked)
		{
			state->layer.displaySyncEnabled = NO;
		}
		else if (supports_unlocked)
		{
			state->layer.displaySyncEnabled = YES;
		}
	});
	state->effective_mode = requested == PRESENT_UNLOCKED && supports_unlocked ? PRESENT_UNLOCKED : PRESENT_FIFO_LIKE;
}

static void release_device_objects_locked(MetalControlState *state)
{
	for (int index = 0; index < 3; index++)
	{
		if (state->slots[index].texture != nil)
		{
			[state->slots[index].texture release];
			state->slots[index].texture = nil;
			state->slots[index].width = 0;
			state->slots[index].height = 0;
			track_release(state);
		}
	}
	if (state->sampler != nil) { [state->sampler release]; state->sampler = nil; track_release(state); }
	if (state->ui_pipeline != nil) { [state->ui_pipeline release]; state->ui_pipeline = nil; track_release(state); }
	if (state->triangle_pipeline != nil) { [state->triangle_pipeline release]; state->triangle_pipeline = nil; track_release(state); }
	if (state->library != nil) { [state->library release]; state->library = nil; track_release(state); }
	if (state->queue != nil) { [state->queue release]; state->queue = nil; track_release(state); }
	if (state->device != nil) { [state->device release]; state->device = nil; track_release(state); }
}

static id<MTLRenderPipelineState> build_pipeline(MetalControlState *state, NSString *vertex_name,
	NSString *fragment_name, bool blend, NSError **error)
{
	id<MTLFunction> vertex = [state->library newFunctionWithName:vertex_name];
	id<MTLFunction> fragment = [state->library newFunctionWithName:fragment_name];
	if (vertex == nil || fragment == nil)
	{
		[vertex release];
		[fragment release];
		return nil;
	}
	MTLRenderPipelineDescriptor *descriptor = [[[MTLRenderPipelineDescriptor alloc] init] autorelease];
	descriptor.vertexFunction = vertex;
	descriptor.fragmentFunction = fragment;
	descriptor.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
	if (blend)
	{
		descriptor.colorAttachments[0].blendingEnabled = YES;
		descriptor.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorOne;
		descriptor.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
		descriptor.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorOne;
		descriptor.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
	}
	id<MTLRenderPipelineState> pipeline = [state->device newRenderPipelineStateWithDescriptor:descriptor error:error];
	[vertex release];
	[fragment release];
	return pipeline;
}

static bool build_device_objects_locked(MetalControlState *state, id<MTLDevice> device, NSString *failure_stage)
{
	release_device_objects_locked(state);
	state->device = [device retain];
	if (state->device == nil)
	{
		state->counters[COUNTER_INIT_ERRORS]++;
		return false;
	}
	track_create(state);
	state->queue = [state->device newCommandQueue];
	if (state->queue == nil)
	{
		state->counters[COUNTER_INIT_ERRORS]++;
		return false;
	}
	track_create(state);
	if ([failure_stage isEqualToString:@"shader"])
	{
		state->counters[COUNTER_SHADER_ERRORS]++;
		return false;
	}
	NSError *error = nil;
	state->library = [state->device newLibraryWithSource:SHADER_SOURCE options:nil error:&error];
	if (state->library == nil)
	{
		state->counters[COUNTER_SHADER_ERRORS]++;
		return false;
	}
	track_create(state);
	if ([failure_stage isEqualToString:@"pipeline"])
	{
		state->counters[COUNTER_PIPELINE_ERRORS]++;
		return false;
	}
	state->triangle_pipeline = build_pipeline(state, @"triangle_vertex", @"triangle_fragment", false, &error);
	if (state->triangle_pipeline == nil)
	{
		state->counters[COUNTER_PIPELINE_ERRORS]++;
		return false;
	}
	track_create(state);
	state->ui_pipeline = build_pipeline(state, @"ui_vertex", @"ui_fragment", true, &error);
	if (state->ui_pipeline == nil)
	{
		state->counters[COUNTER_PIPELINE_ERRORS]++;
		return false;
	}
	track_create(state);
	MTLSamplerDescriptor *sampler_descriptor = [[[MTLSamplerDescriptor alloc] init] autorelease];
	sampler_descriptor.minFilter = MTLSamplerMinMagFilterNearest;
	sampler_descriptor.magFilter = MTLSamplerMinMagFilterNearest;
	sampler_descriptor.sAddressMode = MTLSamplerAddressModeClampToEdge;
	sampler_descriptor.tAddressMode = MTLSamplerAddressModeClampToEdge;
	state->sampler = [state->device newSamplerStateWithDescriptor:sampler_descriptor];
	if (state->sampler == nil)
	{
		state->counters[COUNTER_INIT_ERRORS]++;
		return false;
	}
	track_create(state);
	__block CAMetalLayer *layer = state->layer;
	dispatch_main_sync(^{
		layer.device = state->device;
		layer.pixelFormat = MTLPixelFormatBGRA8Unorm;
		layer.framebufferOnly = YES;
		layer.maximumDrawableCount = 3;
		layer.allowsNextDrawableTimeout = YES;
	});
	return true;
}

static id<MTLDevice> preferred_device(MetalControlState *state)
{
	__block id<MTLDevice> preferred = nil;
	dispatch_main_sync(^{
		if (@available(macOS 10.15, *))
		{
			preferred = [state->layer.preferredDevice retain];
		}
	});
	if (preferred == nil)
	{
		preferred = MTLCreateSystemDefaultDevice();
	}
	return [preferred autorelease];
}

static int find_free_slot(MetalControlState *state)
{
	for (int index = 0; index < 3; index++)
	{
		if (!state->slots[index].busy)
		{
			return index;
		}
	}
	return -1;
}

static bool ensure_slot_texture_locked(MetalControlState *state, int slot_index, NSUInteger width, NSUInteger height)
{
	TextureSlot *slot = &state->slots[slot_index];
	if (slot->texture != nil && slot->width == width && slot->height == height)
	{
		return true;
	}
	if (slot->texture != nil)
	{
		[slot->texture release];
		slot->texture = nil;
		track_release(state);
	}
	MTLTextureDescriptor *descriptor = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm
		width:width height:height mipmapped:NO];
	descriptor.usage = MTLTextureUsageShaderRead;
	descriptor.storageMode = MTLStorageModeShared;
	slot->texture = [state->device newTextureWithDescriptor:descriptor];
	if (slot->texture == nil)
	{
		state->counters[COUNTER_INIT_ERRORS]++;
		return false;
	}
	slot->width = width;
	slot->height = height;
	state->counters[COUNTER_RESIZE_REBUILDS]++;
	track_create(state);
	return true;
}

static void write_synthetic_ui(uint8_t *bytes, NSUInteger width, NSUInteger height, uint64_t frame_id)
{
	for (NSUInteger y = 0; y < height; y++)
	{
		for (NSUInteger x = 0; x < width; x++)
		{
			NSUInteger index = (y * width + x) * 4;
			if (x < width / 2 && y < height / 2)
			{
				bytes[index] = 0; bytes[index + 1] = 0; bytes[index + 2] = 0; bytes[index + 3] = 0;
			}
			else if (x >= width / 2 && y < height / 2)
			{
				bytes[index] = 0; bytes[index + 1] = 0; bytes[index + 2] = 128; bytes[index + 3] = 128;
			}
			else if (x < width / 2)
			{
				bytes[index] = 255; bytes[index + 1] = 0; bytes[index + 2] = 0; bytes[index + 3] = 255;
			}
			else
			{
				uint8_t alpha = 192;
				uint8_t red = (uint8_t) ((frame_id * 5 + x * 3 + y) & 0xff);
				uint8_t green = (uint8_t) ((frame_id * 3 + x + y * 5) & 0xff);
				uint8_t blue = (uint8_t) ((frame_id * 7 + x * 2 + y * 3) & 0xff);
				bytes[index] = (uint8_t) ((blue * alpha + 127) / 255);
				bytes[index + 1] = (uint8_t) ((green * alpha + 127) / 255);
				bytes[index + 2] = (uint8_t) ((red * alpha + 127) / 255);
				bytes[index + 3] = alpha;
			}
		}
	}
}

JNIEXPORT jlong JNICALL Java_rs117_hd_spikes_macos_control_MetalControlNative_nativeCreate(
	JNIEnv *env, jclass type, jlong layer_handle, jstring log_path, jstring requested_mode, jstring failure_stage)
{
	(void) type;
	@autoreleasepool
	{
		MetalControlState *state = calloc(1, sizeof(*state));
		if (state == NULL)
		{
			throw_exception(env, "java/lang/OutOfMemoryError", "Unable to allocate Metal control state.");
			return 0;
		}
		if (pthread_mutex_init(&state->mutex, NULL) != 0 || pthread_cond_init(&state->drained, NULL) != 0)
		{
			free(state);
			throw_exception(env, "java/lang/IllegalStateException", "Unable to initialize Metal control synchronization.");
			return 0;
		}
		state->accepting = true;
		track_create(state);
		state->layer = (CAMetalLayer *) (intptr_t) layer_handle;
		NSString *mode_string = string_from_java(env, requested_mode);
		NSString *failure_string = string_from_java(env, failure_stage);
		NSString *path = string_from_java(env, log_path);
		state->requested_mode = parse_present_mode(mode_string);
		state->effective_mode = PRESENT_FIFO_LIKE;
		if (state->layer == nil || ![state->layer isKindOfClass:[CAMetalLayer class]])
		{
			state->counters[COUNTER_INIT_ERRORS]++;
		}
		if (path != nil)
		{
			state->log = fopen(path.fileSystemRepresentation, "w");
			if (state->log == NULL)
			{
				state->counters[COUNTER_INIT_ERRORS]++;
			}
		}
		if ([failure_string isEqualToString:@"init"])
		{
			state->counters[COUNTER_INIT_ERRORS]++;
		}
		if (state->counters[COUNTER_INIT_ERRORS] == 0)
		{
			id<MTLDevice> device = preferred_device(state);
			state->ready = build_device_objects_locked(state, device, failure_string);
			configure_present_mode_locked(state, state->requested_mode);
		}
		log_run_start(state);
		return (jlong) (intptr_t) state;
	}
}

JNIEXPORT jboolean JNICALL Java_rs117_hd_spikes_macos_control_MetalControlNative_nativeReady(JNIEnv *env, jclass type, jlong handle)
{
	(void) type;
	MetalControlState *state = state_from_handle(env, handle);
	if (state == NULL) return JNI_FALSE;
	pthread_mutex_lock(&state->mutex);
	bool ready = state->ready && state->accepting;
	pthread_mutex_unlock(&state->mutex);
	return ready ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_rs117_hd_spikes_macos_control_MetalControlNative_nativeSkipSuspended(
	JNIEnv *env, jclass type, jlong handle, jlong frame_id)
{
	(void) type;
	MetalControlState *state = state_from_handle(env, handle);
	if (state == NULL) return OUTCOME_ERROR;
	pthread_mutex_lock(&state->mutex);
	if (!state->accepting)
	{
		pthread_mutex_unlock(&state->mutex);
		return OUTCOME_REJECTED;
	}
	state->counters[COUNTER_SKIPPED_SUSPENDED]++;
	log_frame(state, (uint64_t) frame_id, 0, 0, state->requested_mode, state->effective_mode,
		OUTCOME_SKIPPED_SUSPENDED, 0, 0, 0, 0, 0, nil, false, false, NULL);
	pthread_mutex_unlock(&state->mutex);
	return OUTCOME_SKIPPED_SUSPENDED;
}

JNIEXPORT jint JNICALL Java_rs117_hd_spikes_macos_control_MetalControlNative_nativeRender(
	JNIEnv *env, jclass type, jlong handle, jint width, jint height, jlong frame_id, jlong ui_generate_ns, jbyteArray ui_bytes)
{
	(void) type;
	@autoreleasepool
	{
		MetalControlState *state = state_from_handle(env, handle);
		if (state == NULL) return OUTCOME_ERROR;
		uint64_t frame_start = monotonic_ns();
		pthread_mutex_lock(&state->mutex);
		if (!state->accepting || !state->ready)
		{
			pthread_mutex_unlock(&state->mutex);
			return OUTCOME_REJECTED;
		}
		if (width <= 0 || height <= 0)
		{
			state->counters[COUNTER_SKIPPED_SUSPENDED]++;
			log_frame(state, (uint64_t) frame_id, 0, 0, state->requested_mode, state->effective_mode,
				OUTCOME_SKIPPED_SUSPENDED, 0, 0, 0, 0, monotonic_ns() - frame_start, nil, false, false, NULL);
			pthread_mutex_unlock(&state->mutex);
			return OUTCOME_SKIPPED_SUSPENDED;
		}
		id<MTLDevice> preferred = preferred_device(state);
		if (preferred != nil && preferred != state->device)
		{
			if (state->inflight != 0)
			{
				state->counters[COUNTER_SKIPPED_IN_FLIGHT]++;
				log_frame(state, (uint64_t) frame_id, (NSUInteger) width, (NSUInteger) height,
					state->requested_mode, state->effective_mode, OUTCOME_SKIPPED_IN_FLIGHT,
					(uint64_t) ui_generate_ns, 0, 0, 0, (uint64_t) ui_generate_ns + monotonic_ns() - frame_start, nil, false, false, NULL);
				pthread_mutex_unlock(&state->mutex);
				return OUTCOME_SKIPPED_IN_FLIGHT;
			}
			if (!build_device_objects_locked(state, preferred, @""))
			{
				state->ready = false;
				pthread_mutex_unlock(&state->mutex);
				return OUTCOME_ERROR;
			}
			state->counters[COUNTER_DEVICE_REBUILDS]++;
			configure_present_mode_locked(state, state->requested_mode);
		}
		int slot_index = find_free_slot(state);
		if (slot_index < 0)
		{
			state->counters[COUNTER_SKIPPED_IN_FLIGHT]++;
			log_frame(state, (uint64_t) frame_id, (NSUInteger) width, (NSUInteger) height,
				state->requested_mode, state->effective_mode, OUTCOME_SKIPPED_IN_FLIGHT,
				(uint64_t) ui_generate_ns, 0, 0, 0, (uint64_t) ui_generate_ns + monotonic_ns() - frame_start, nil, false, false, NULL);
			pthread_mutex_unlock(&state->mutex);
			return OUTCOME_SKIPPED_IN_FLIGHT;
		}
		TextureSlot *slot = &state->slots[slot_index];
		slot->busy = true;
		if (!ensure_slot_texture_locked(state, slot_index, (NSUInteger) width, (NSUInteger) height))
		{
			slot->busy = false;
			pthread_mutex_unlock(&state->mutex);
			return OUTCOME_ERROR;
		}
		jlong expected_length = (jlong) width * (jlong) height * 4;
		if (ui_bytes == NULL || (*env)->GetArrayLength(env, ui_bytes) != expected_length)
		{
			slot->busy = false;
			pthread_mutex_unlock(&state->mutex);
			throw_exception(env, "java/lang/IllegalArgumentException", "UI upload must contain exact premultiplied BGRA bytes.");
			return OUTCOME_ERROR;
		}
		uint64_t upload_start = monotonic_ns();
		jbyte *bytes = (*env)->GetByteArrayElements(env, ui_bytes, NULL);
		if (bytes == NULL)
		{
			slot->busy = false;
			pthread_mutex_unlock(&state->mutex);
			return OUTCOME_ERROR;
		}
		MTLRegion region = MTLRegionMake2D(0, 0, (NSUInteger) width, (NSUInteger) height);
		[slot->texture replaceRegion:region mipmapLevel:0 withBytes:bytes bytesPerRow:(NSUInteger) width * 4];
		(*env)->ReleaseByteArrayElements(env, ui_bytes, bytes, JNI_ABORT);
		uint64_t upload_ns = monotonic_ns() - upload_start;
		state->counters[COUNTER_UI_UPLOAD_BYTES] += (uint64_t) expected_length;

		id<CAMetalDrawable> drawable = [state->layer nextDrawable];
		if (drawable == nil)
		{
			slot->busy = false;
			state->counters[COUNTER_NIL_DRAWABLE]++;
			log_frame(state, (uint64_t) frame_id, (NSUInteger) width, (NSUInteger) height,
				state->requested_mode, state->effective_mode, OUTCOME_NIL_DRAWABLE,
				(uint64_t) ui_generate_ns, upload_ns, 0, 0, (uint64_t) ui_generate_ns + monotonic_ns() - frame_start, nil, false, false, NULL);
			pthread_mutex_unlock(&state->mutex);
			return OUTCOME_NIL_DRAWABLE;
		}

		uint64_t encode_start = monotonic_ns();
		id<MTLCommandBuffer> command_buffer = [state->queue commandBuffer];
		if (command_buffer == nil)
		{
			slot->busy = false;
			state->counters[COUNTER_COMMAND_ERRORS]++;
			pthread_mutex_unlock(&state->mutex);
			return OUTCOME_ERROR;
		}
		MTLRenderPassDescriptor *pass = [MTLRenderPassDescriptor renderPassDescriptor];
		pass.colorAttachments[0].texture = drawable.texture;
		pass.colorAttachments[0].loadAction = MTLLoadActionClear;
		pass.colorAttachments[0].storeAction = MTLStoreActionStore;
		double pulse = 0.5 + 0.5 * sin((double) frame_id * 0.025);
		pass.colorAttachments[0].clearColor = MTLClearColorMake(0.04 + pulse * 0.08, 0.08, 0.14 + pulse * 0.08, 1.0);
		id<MTLRenderCommandEncoder> encoder = [command_buffer renderCommandEncoderWithDescriptor:pass];
		if (encoder == nil)
		{
			slot->busy = false;
			state->counters[COUNTER_COMMAND_ERRORS]++;
			pthread_mutex_unlock(&state->mutex);
			return OUTCOME_ERROR;
		}
		float phase = (float) ((double) frame_id * 0.0125);
		[encoder setRenderPipelineState:state->triangle_pipeline];
		[encoder setVertexBytes:&phase length:sizeof(phase) atIndex:0];
		[encoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:3];
		[encoder setRenderPipelineState:state->ui_pipeline];
		[encoder setFragmentTexture:slot->texture atIndex:0];
		[encoder setFragmentSamplerState:state->sampler atIndex:0];
		[encoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:6];
		[encoder endEncoding];
		uint64_t encode_ns = monotonic_ns() - encode_start;

		FrameTiming *timing = calloc(1, sizeof(*timing));
		if (timing == NULL)
		{
			slot->busy = false;
			state->counters[COUNTER_INIT_ERRORS]++;
			pthread_mutex_unlock(&state->mutex);
			return OUTCOME_ERROR;
		}
		track_create(state);
		timing->frame_id = (uint64_t) frame_id;
		timing->width = (NSUInteger) width;
		timing->height = (NSUInteger) height;
		timing->requested_mode = state->requested_mode;
		timing->effective_mode = state->effective_mode;
		timing->ui_generate_ns = (uint64_t) ui_generate_ns;
		timing->ui_upload_ns = upload_ns;
		timing->encode_ns = encode_ns;
		timing->slot_index = slot_index;
		state->inflight++;
		state->counters[COUNTER_SUBMITTED]++;
		state->counters[COUNTER_PRESENT_REQUESTED]++;
		if (state->inflight > state->counters[COUNTER_MAX_IN_FLIGHT])
		{
			state->counters[COUNTER_MAX_IN_FLIGHT] = state->inflight;
		}
		[command_buffer addCompletedHandler:^(id<MTLCommandBuffer> completed) {
			@autoreleasepool
			{
				pthread_mutex_lock(&state->mutex);
				if (completed.status == MTLCommandBufferStatusError)
				{
					state->counters[COUNTER_COMMAND_ERRORS]++;
				}
				state->counters[COUNTER_COMPLETED]++;
				state->slots[timing->slot_index].busy = false;
				if (state->inflight > 0) state->inflight--;
				log_frame(state, timing->frame_id, timing->width, timing->height,
					timing->requested_mode, timing->effective_mode, OUTCOME_SUBMITTED,
					timing->ui_generate_ns, timing->ui_upload_ns, timing->encode_ns, timing->submit_ns, timing->total_ns,
					completed, true, true, completed.status == MTLCommandBufferStatusError ? "error" : NULL);
				track_release(state);
				free(timing);
				pthread_cond_broadcast(&state->drained);
				pthread_mutex_unlock(&state->mutex);
			}
		}];
		uint64_t submit_start = monotonic_ns();
		[command_buffer presentDrawable:drawable];
		[command_buffer commit];
		timing->submit_ns = monotonic_ns() - submit_start;
		timing->total_ns = timing->ui_generate_ns + monotonic_ns() - frame_start;
		pthread_mutex_unlock(&state->mutex);
		return OUTCOME_SUBMITTED;
	}
}

JNIEXPORT void JNICALL Java_rs117_hd_spikes_macos_control_MetalControlNative_nativeSetPresentMode(
	JNIEnv *env, jclass type, jlong handle, jstring requested_mode)
{
	(void) type;
	@autoreleasepool
	{
		MetalControlState *state = state_from_handle(env, handle);
		if (state == NULL) return;
		NSString *mode = string_from_java(env, requested_mode);
		pthread_mutex_lock(&state->mutex);
		if (!state->accepting)
		{
			pthread_mutex_unlock(&state->mutex);
			throw_exception(env, "java/lang/IllegalStateException", "Metal control renderer is closing.");
			return;
		}
		configure_present_mode_locked(state, parse_present_mode(mode));
		pthread_mutex_unlock(&state->mutex);
	}
}

static jlongArray counters_array(JNIEnv *env, const uint64_t *counters)
{
	jlongArray array = (*env)->NewLongArray(env, COUNTER_COUNT);
	if (array == NULL) return NULL;
	jlong values[COUNTER_COUNT];
	for (int index = 0; index < COUNTER_COUNT; index++) values[index] = (jlong) counters[index];
	(*env)->SetLongArrayRegion(env, array, 0, COUNTER_COUNT, values);
	return array;
}

JNIEXPORT jlongArray JNICALL Java_rs117_hd_spikes_macos_control_MetalControlNative_nativeCounters(
	JNIEnv *env, jclass type, jlong handle)
{
	(void) type;
	MetalControlState *state = state_from_handle(env, handle);
	if (state == NULL) return NULL;
	pthread_mutex_lock(&state->mutex);
	jlongArray array = counters_array(env, state->counters);
	pthread_mutex_unlock(&state->mutex);
	return array;
}

static bool channel_near(uint8_t actual, int expected)
{
	return actual >= expected - 3 && actual <= expected + 3;
}

JNIEXPORT jboolean JNICALL Java_rs117_hd_spikes_macos_control_MetalControlNative_nativeRunReadbackCheck(
	JNIEnv *env, jclass type, jlong handle)
{
	(void) type;
	@autoreleasepool
	{
		MetalControlState *state = state_from_handle(env, handle);
		if (state == NULL) return JNI_FALSE;
		pthread_mutex_lock(&state->mutex);
		if (!state->accepting || !state->ready || state->inflight != 0)
		{
			pthread_mutex_unlock(&state->mutex);
			return JNI_FALSE;
		}
		const NSUInteger width = 8;
		const NSUInteger height = 8;
		MTLTextureDescriptor *render_descriptor = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm
			width:width height:height mipmapped:NO];
		render_descriptor.usage = MTLTextureUsageRenderTarget;
		render_descriptor.storageMode = MTLStorageModeShared;
		id<MTLTexture> render_texture = [state->device newTextureWithDescriptor:render_descriptor];
		MTLTextureDescriptor *ui_descriptor = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm
			width:width height:height mipmapped:NO];
		ui_descriptor.usage = MTLTextureUsageShaderRead;
		ui_descriptor.storageMode = MTLStorageModeShared;
		id<MTLTexture> ui_texture = [state->device newTextureWithDescriptor:ui_descriptor];
		if (render_texture == nil || ui_texture == nil)
		{
			[render_texture release];
			[ui_texture release];
			pthread_mutex_unlock(&state->mutex);
			return JNI_FALSE;
		}
		track_create(state);
		track_create(state);
		uint8_t ui[8 * 8 * 4];
		write_synthetic_ui(ui, width, height, 0);
		[ui_texture replaceRegion:MTLRegionMake2D(0, 0, width, height) mipmapLevel:0 withBytes:ui bytesPerRow:width * 4];
		id<MTLCommandBuffer> command_buffer = [state->queue commandBuffer];
		MTLRenderPassDescriptor *pass = [MTLRenderPassDescriptor renderPassDescriptor];
		pass.colorAttachments[0].texture = render_texture;
		pass.colorAttachments[0].loadAction = MTLLoadActionClear;
		pass.colorAttachments[0].storeAction = MTLStoreActionStore;
		pass.colorAttachments[0].clearColor = MTLClearColorMake(0.10, 0.15, 0.20, 1.0);
		id<MTLRenderCommandEncoder> encoder = [command_buffer renderCommandEncoderWithDescriptor:pass];
		float phase = 0.0f;
		[encoder setRenderPipelineState:state->triangle_pipeline];
		[encoder setVertexBytes:&phase length:sizeof(phase) atIndex:0];
		[encoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:3];
		[encoder setRenderPipelineState:state->ui_pipeline];
		[encoder setFragmentTexture:ui_texture atIndex:0];
		[encoder setFragmentSamplerState:state->sampler atIndex:0];
		[encoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:6];
		[encoder endEncoding];
		[command_buffer commit];
		[command_buffer waitUntilCompleted];
		uint8_t result[8 * 8 * 4];
		[render_texture getBytes:result bytesPerRow:width * 4 fromRegion:MTLRegionMake2D(0, 0, width, height) mipmapLevel:0];
		NSUInteger top_right = (1 * width + 6) * 4;
		NSUInteger bottom_left = (6 * width + 1) * 4;
		NSUInteger center = (3 * width + 3) * 4;
		bool valid = command_buffer.status == MTLCommandBufferStatusCompleted &&
			channel_near(result[top_right], 26) && channel_near(result[top_right + 1], 19) &&
			channel_near(result[top_right + 2], 140) && channel_near(result[top_right + 3], 255) &&
			channel_near(result[bottom_left], 255) && channel_near(result[bottom_left + 1], 0) &&
			channel_near(result[bottom_left + 2], 0) && channel_near(result[bottom_left + 3], 255) &&
			result[center + 1] > 150 && result[center + 2] < 100;
		[render_texture release];
		[ui_texture release];
		track_release(state);
		track_release(state);
		pthread_mutex_unlock(&state->mutex);
		return valid ? JNI_TRUE : JNI_FALSE;
	}
}

JNIEXPORT jlongArray JNICALL Java_rs117_hd_spikes_macos_control_MetalControlNative_nativeClose(
	JNIEnv *env, jclass type, jlong handle)
{
	(void) type;
	@autoreleasepool
	{
		MetalControlState *state = state_from_handle(env, handle);
		if (state == NULL) return NULL;
		pthread_mutex_lock(&state->mutex);
		if (!state->accepting)
		{
			pthread_mutex_unlock(&state->mutex);
			throw_exception(env, "java/lang/IllegalStateException", "Metal control renderer is already closing.");
			return NULL;
		}
		state->accepting = false;
		while (state->inflight != 0)
		{
			pthread_cond_wait(&state->drained, &state->mutex);
		}
		state->ready = false;
		__block CAMetalLayer *layer = state->layer;
		__block id<MTLDevice> device = state->device;
		dispatch_main_sync(^{
			if (layer.device == device) layer.device = nil;
		});
		release_device_objects_locked(state);
		state->layer = nil;
		track_release(state);
		if (state->log != NULL)
		{
			fprintf(state->log,
				"{\"schema\":\"rlhd.renderer.timing/v1\",\"type\":\"run_end\",\"backend\":\"metal-control\","
				"\"timestamp_ns\":%llu,\"requested_present_mode\":\"%s\",\"effective_present_mode\":\"%s\",\"counters\":",
				(unsigned long long) monotonic_ns(), present_mode_name(state->requested_mode), present_mode_name(state->effective_mode));
			write_counters(state->log, state->counters);
			fprintf(state->log, ",\"error\":%s}\n", state->counters[COUNTER_COMMAND_ERRORS] == 0 ? "null" : "\"command-buffer-error\"");
			fflush(state->log);
			fclose(state->log);
			state->log = NULL;
		}
		jlongArray final_counters = counters_array(env, state->counters);
		pthread_mutex_unlock(&state->mutex);
		pthread_cond_destroy(&state->drained);
		pthread_mutex_destroy(&state->mutex);
		free(state);
		return final_counters;
	}
}
