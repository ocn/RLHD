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
	COUNTER_PRESENTATION_CALLBACKS,
	COUNTER_PRESENTATION_DROPPED,
	COUNTER_PRESENTATION_TIMEOUTS,
	COUNTER_PRESENT_MODE_DIVERGENCES,
	COUNTER_DRAWABLE_ACQUISITION_REQUESTS,
	COUNTER_DRAWABLE_ACQUISITION_COMPLETIONS,
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

@interface RlhdPresentationToken : NSObject
{
	pthread_mutex_t token_mutex;
	dispatch_semaphore_t token_semaphore;
	bool token_done;
	double token_presented_time;
}
- (void)markPresentedTime:(double)presentedTime;
- (bool)waitUntil:(dispatch_time_t)deadline;
- (bool)snapshotPresentedTime:(double *)presentedTime;
@end

@implementation RlhdPresentationToken
- (instancetype)init
{
	self = [super init];
	if (self != nil)
	{
		pthread_mutex_init(&token_mutex, NULL);
		token_semaphore = dispatch_semaphore_create(0);
	}
	return self;
}

- (void)dealloc
{
	if (token_semaphore != NULL) dispatch_release(token_semaphore);
	pthread_mutex_destroy(&token_mutex);
	[super dealloc];
}

- (void)markPresentedTime:(double)presentedTime
{
	pthread_mutex_lock(&token_mutex);
	if (!token_done)
	{
		token_done = true;
		token_presented_time = presentedTime;
		dispatch_semaphore_signal(token_semaphore);
	}
	pthread_mutex_unlock(&token_mutex);
}

- (bool)waitUntil:(dispatch_time_t)deadline
{
	double ignored;
	if ([self snapshotPresentedTime:&ignored]) return true;
	return dispatch_semaphore_wait(token_semaphore, deadline) == 0;
}

- (bool)snapshotPresentedTime:(double *)presentedTime
{
	pthread_mutex_lock(&token_mutex);
	bool done = token_done;
	if (done && presentedTime != NULL) *presentedTime = token_presented_time;
	pthread_mutex_unlock(&token_mutex);
	return done;
}
@end

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
	uint64_t submit_host_ns;
	int slot_index;
	bool gpu_done;
	bool command_error;
	double gpu_start_time;
	double gpu_end_time;
	const char *presentation_status;
	double presented_time;
	id<CAMetalDrawable> drawable;
	RlhdPresentationToken *presentation_token;
	struct FrameTiming *next;
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
	NSUInteger pending_frame_callbacks;
	FrameTiming *completed_frames;
	dispatch_queue_t drawable_queue;
	bool acquisition_pending;
	bool acquisition_returned_nil;
	uint64_t acquisition_generation;
	id<CAMetalDrawable> ready_drawable;
	NSUInteger ready_drawable_width;
	NSUInteger ready_drawable_height;
	NSUInteger requested_drawable_width;
	NSUInteger requested_drawable_height;
	bool extent_valid;
	bool suspended;
	bool stall_drawable;
	bool ignore_present_mode_setter;
	bool unsupported_present_callback;
	bool timeout_present_callback;
	bool dropped_present_callback;
	bool bypass_upload;
	bool bypass_present;
	bool fail_close_preconsume_once;
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
		"\"max_in_flight\":%llu,\"presentation_callbacks\":%llu,\"presentation_dropped\":%llu,"
		"\"presentation_timeouts\":%llu,\"present_mode_divergences\":%llu,"
		"\"drawable_acquisition_requests\":%llu,\"drawable_acquisition_completions\":%llu}",
		(unsigned long long) counters[0], (unsigned long long) counters[1],
		(unsigned long long) counters[2], (unsigned long long) counters[3],
		(unsigned long long) counters[4], (unsigned long long) counters[5],
		(unsigned long long) counters[6], (unsigned long long) counters[7],
		(unsigned long long) counters[8], (unsigned long long) counters[9],
		(unsigned long long) counters[10], (unsigned long long) counters[11],
		(unsigned long long) counters[12], (unsigned long long) counters[13],
		(unsigned long long) counters[14], (unsigned long long) counters[15],
		(unsigned long long) counters[16], (unsigned long long) counters[17],
		(unsigned long long) counters[18], (unsigned long long) counters[19],
		(unsigned long long) counters[20], (unsigned long long) counters[21]);
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
	uint64_t encode_ns, uint64_t submit_ns, uint64_t total_ns, uint64_t submit_host_ns, double gpu_start_time, double gpu_end_time,
	bool present_requested, bool drawable_available, const char *presentation_status, double presented_time, const char *error)
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
	if (gpu_start_time > 0.0 && gpu_end_time >= gpu_start_time)
	{
		uint64_t start = (uint64_t) (gpu_start_time * 1000000000.0);
		uint64_t end = (uint64_t) (gpu_end_time * 1000000000.0);
		fprintf(state->log, "\"gpu_ns\":{\"start\":%llu,\"end\":%llu,\"duration\":%llu},",
			(unsigned long long) start, (unsigned long long) end, (unsigned long long) (end - start));
	}
	else
	{
		fprintf(state->log, "\"gpu_ns\":null,");
	}
	fprintf(state->log, "\"present\":{\"requested\":%s,\"drawable_available\":%s,\"callback_status\":\"%s\",",
		present_requested ? "true" : "false", drawable_available ? "true" : "false",
		presentation_status == NULL ? "not-requested" : presentation_status);
	if (presented_time > 0.0)
	{
		uint64_t presented_ns = (uint64_t) (presented_time * 1000000000.0);
		uint64_t latency_ns = presented_ns >= submit_host_ns ? presented_ns - submit_host_ns : 0;
		fprintf(state->log, "\"presented_time_ns\":%llu,\"latency_ns\":%llu},\"counters\":",
			(unsigned long long) presented_ns, (unsigned long long) latency_ns);
	}
	else
	{
		fprintf(state->log, "\"presented_time_ns\":null,\"latency_ns\":null},\"counters\":");
	}
	write_counters(state->log, state->counters);
	if (error == NULL) fprintf(state->log, ",\"error\":null}\n");
	else fprintf(state->log, ",\"error\":\"%s\"}\n", error);
	fflush(state->log);
}

static void configure_present_mode_locked(MetalControlState *state, PresentMode requested)
{
	state->requested_mode = requested;
	__block bool supports_unlocked = false;
	__block bool observed_sync = true;
	dispatch_main_sync(^{
		state->layer.presentsWithTransaction = NO;
		supports_unlocked = [state->layer respondsToSelector:@selector(setDisplaySyncEnabled:)] &&
			[state->layer respondsToSelector:@selector(displaySyncEnabled)];
		if (!state->ignore_present_mode_setter && requested == PRESENT_UNLOCKED && supports_unlocked)
		{
			state->layer.displaySyncEnabled = NO;
		}
		else if (!state->ignore_present_mode_setter && supports_unlocked)
		{
			state->layer.displaySyncEnabled = YES;
		}
		if (supports_unlocked) observed_sync = state->layer.displaySyncEnabled;
	});
	state->effective_mode = supports_unlocked && !observed_sync ? PRESENT_UNLOCKED : PRESENT_FIFO_LIKE;
	if (state->effective_mode != requested) state->counters[COUNTER_PRESENT_MODE_DIVERGENCES]++;
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

static void release_ready_drawable_locked(MetalControlState *state)
{
	if (state->ready_drawable != nil)
	{
		[state->ready_drawable release];
		state->ready_drawable = nil;
		track_release(state);
	}
	state->ready_drawable_width = 0;
	state->ready_drawable_height = 0;
}

static void invalidate_drawable_acquisition_locked(MetalControlState *state)
{
	state->acquisition_generation++;
	state->acquisition_returned_nil = false;
	release_ready_drawable_locked(state);
}

static void suspend_drawable_acquisition_locked(MetalControlState *state)
{
	if (!state->suspended)
	{
		invalidate_drawable_acquisition_locked(state);
		state->suspended = true;
	}
}

static void update_drawable_extent_locked(MetalControlState *state, NSUInteger width, NSUInteger height)
{
	if (!state->extent_valid || state->suspended ||
		state->requested_drawable_width != width || state->requested_drawable_height != height)
	{
		invalidate_drawable_acquisition_locked(state);
		state->requested_drawable_width = width;
		state->requested_drawable_height = height;
		state->extent_valid = true;
		state->suspended = false;
	}
}

static void schedule_drawable_acquisition_locked(MetalControlState *state)
{
	if (!state->accepting || !state->extent_valid || state->suspended ||
		state->acquisition_pending || state->ready_drawable != nil)
	{
		return;
	}
	state->acquisition_pending = true;
	state->counters[COUNTER_DRAWABLE_ACQUISITION_REQUESTS]++;
	uint64_t generation = state->acquisition_generation;
	NSUInteger requested_width = state->requested_drawable_width;
	NSUInteger requested_height = state->requested_drawable_height;
	dispatch_async(state->drawable_queue, ^{
		@autoreleasepool
		{
			id<CAMetalDrawable> acquired = nil;
			if (state->stall_drawable)
			{
				struct timespec pause = {0, 250000000};
				nanosleep(&pause, NULL);
			}
			else
			{
				acquired = [[state->layer nextDrawable] retain];
			}
			NSUInteger acquired_width = acquired == nil ? 0 : acquired.texture.width;
			NSUInteger acquired_height = acquired == nil ? 0 : acquired.texture.height;
			pthread_mutex_lock(&state->mutex);
			state->counters[COUNTER_DRAWABLE_ACQUISITION_COMPLETIONS]++;
			bool request_is_current = state->accepting && state->extent_valid && !state->suspended &&
				generation == state->acquisition_generation && requested_width == state->requested_drawable_width &&
				requested_height == state->requested_drawable_height;
			if (request_is_current && acquired != nil && state->ready_drawable == nil &&
				acquired_width == requested_width && acquired_height == requested_height)
			{
				state->ready_drawable = acquired;
				state->ready_drawable_width = acquired_width;
				state->ready_drawable_height = acquired_height;
				track_create(state);
				acquired = nil;
			}
			else if (request_is_current && acquired == nil)
			{
				state->acquisition_returned_nil = true;
				state->counters[COUNTER_NIL_DRAWABLE]++;
			}
			[acquired release];
			state->acquisition_pending = false;
			pthread_cond_broadcast(&state->drained);
			pthread_mutex_unlock(&state->mutex);
		}
	});
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

static bool upload_java_ui(JNIEnv *env, MetalControlState *state, jbyteArray ui_bytes,
	id<MTLTexture> texture, NSUInteger width, NSUInteger height)
{
	jlong expected_length = (jlong) width * (jlong) height * 4;
	if (ui_bytes == NULL || (*env)->GetArrayLength(env, ui_bytes) != expected_length)
	{
		throw_exception(env, "java/lang/IllegalArgumentException", "UI upload must contain exact premultiplied BGRA bytes.");
		return false;
	}
	jbyte *bytes = (*env)->GetByteArrayElements(env, ui_bytes, NULL);
	if (bytes == NULL) return false;
	if (!state->bypass_upload)
	{
		[texture replaceRegion:MTLRegionMake2D(0, 0, width, height) mipmapLevel:0
			withBytes:bytes bytesPerRow:width * 4];
	}
	(*env)->ReleaseByteArrayElements(env, ui_bytes, bytes, JNI_ABORT);
	return true;
}

static bool encode_scene(MetalControlState *state, id<MTLCommandBuffer> command_buffer,
	id<MTLTexture> target, id<MTLTexture> ui_texture, uint64_t frame_id, bool fixed_clear)
{
	MTLRenderPassDescriptor *pass = [MTLRenderPassDescriptor renderPassDescriptor];
	pass.colorAttachments[0].texture = target;
	pass.colorAttachments[0].loadAction = MTLLoadActionClear;
	pass.colorAttachments[0].storeAction = MTLStoreActionStore;
	double pulse = fixed_clear ? 0.75 : 0.5 + 0.5 * sin((double) frame_id * 0.025);
	pass.colorAttachments[0].clearColor = fixed_clear ? MTLClearColorMake(0.10, 0.15, 0.20, 1.0) :
		MTLClearColorMake(0.04 + pulse * 0.08, 0.08, 0.14 + pulse * 0.08, 1.0);
	id<MTLRenderCommandEncoder> encoder = [command_buffer renderCommandEncoderWithDescriptor:pass];
	if (encoder == nil) return false;
	float phase = fixed_clear ? 0.0f : (float) ((double) frame_id * 0.0125);
	[encoder setRenderPipelineState:state->triangle_pipeline];
	[encoder setVertexBytes:&phase length:sizeof(phase) atIndex:0];
	[encoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:3];
	[encoder setRenderPipelineState:state->ui_pipeline];
	[encoder setFragmentTexture:ui_texture atIndex:0];
	[encoder setFragmentSamplerState:state->sampler atIndex:0];
	[encoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:6];
	[encoder endEncoding];
	return true;
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
		state->drawable_queue = dispatch_queue_create("rs117.hd.metal-control.drawable", DISPATCH_QUEUE_SERIAL);
		if (state->drawable_queue != NULL) track_create(state);
		else state->counters[COUNTER_INIT_ERRORS]++;
		state->layer = (CAMetalLayer *) (intptr_t) layer_handle;
		NSString *mode_string = string_from_java(env, requested_mode);
		NSString *failure_string = string_from_java(env, failure_stage);
		NSString *path = string_from_java(env, log_path);
		state->requested_mode = parse_present_mode(mode_string);
		state->effective_mode = PRESENT_FIFO_LIKE;
		state->stall_drawable = [failure_string isEqualToString:@"stalled-drawable"];
		state->ignore_present_mode_setter = [failure_string isEqualToString:@"ignored-present-mode"];
		state->unsupported_present_callback = [failure_string isEqualToString:@"unsupported-present-callback"];
		state->timeout_present_callback = [failure_string isEqualToString:@"timeout-present-callback"];
		state->dropped_present_callback = [failure_string isEqualToString:@"dropped-present-callback"];
		state->bypass_upload = [failure_string isEqualToString:@"bypass-upload"];
		state->bypass_present = [failure_string isEqualToString:@"bypass-present"];
		state->fail_close_preconsume_once = [failure_string isEqualToString:@"close-preconsume"];
		if (state->layer == nil || ![state->layer isKindOfClass:[CAMetalLayer class]])
		{
			state->counters[COUNTER_INIT_ERRORS]++;
		}
		else
		{
			dispatch_main_sync(^{ [state->layer retain]; });
			track_create(state);
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

static void finalize_frame_locked(MetalControlState *state, FrameTiming *timing)
{
	log_frame(state, timing->frame_id, timing->width, timing->height,
		timing->requested_mode, timing->effective_mode, OUTCOME_SUBMITTED,
		timing->ui_generate_ns, timing->ui_upload_ns, timing->encode_ns, timing->submit_ns, timing->total_ns,
		timing->submit_host_ns, timing->gpu_start_time, timing->gpu_end_time, true, true,
		timing->presentation_status, timing->presented_time, timing->command_error ? "command-buffer-error" : NULL);
	if (timing->presentation_token != nil)
	{
		[timing->presentation_token release];
		track_release(state);
	}
	[timing->drawable release];
	track_release(state);
	track_release(state);
	free(timing);
}

static void drain_completed_frames_locked(MetalControlState *state, bool closing)
{
	dispatch_time_t deadline = dispatch_time(DISPATCH_TIME_NOW, INT64_C(1000000000));
	FrameTiming **link = &state->completed_frames;
	while (*link != NULL)
	{
		FrameTiming *timing = *link;
		bool settled = timing->presentation_token == nil;
		double presented_time = 0.0;
		if (timing->presentation_token != nil)
		{
			settled = [timing->presentation_token snapshotPresentedTime:&presented_time];
			if (!settled && closing) settled = [timing->presentation_token waitUntil:deadline];
			if (settled) (void) [timing->presentation_token snapshotPresentedTime:&presented_time];
		}
		if (!settled && !closing)
		{
			link = &timing->next;
			continue;
		}
		if (timing->presentation_token != nil && settled)
		{
			timing->presented_time = presented_time;
			if (presented_time > 0.0)
			{
				timing->presentation_status = "presented";
				state->counters[COUNTER_PRESENTATION_CALLBACKS]++;
			}
			else
			{
				timing->presentation_status = "dropped";
				state->counters[COUNTER_PRESENTATION_DROPPED]++;
			}
		}
		else if (timing->presentation_token != nil)
		{
			timing->presentation_status = "callback-timeout";
			state->counters[COUNTER_PRESENTATION_TIMEOUTS]++;
		}
		*link = timing->next;
		finalize_frame_locked(state, timing);
	}
}

static void log_failed_attempt_locked(MetalControlState *state, uint64_t frame_id, NSUInteger width,
	NSUInteger height, uint64_t ui_generate_ns, uint64_t total_ns, FrameOutcome outcome, const char *error)
{
	log_frame(state, frame_id, width, height, state->requested_mode, state->effective_mode, outcome,
		ui_generate_ns, 0, 0, 0, total_ns, 0, 0.0, 0.0, false, false, "not-requested", 0.0, error);
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
		log_failed_attempt_locked(state, (uint64_t) frame_id, 0, 0, 0, 0, OUTCOME_REJECTED, "renderer-closing");
		pthread_mutex_unlock(&state->mutex);
		return OUTCOME_REJECTED;
	}
	suspend_drawable_acquisition_locked(state);
	state->counters[COUNTER_SKIPPED_SUSPENDED]++;
	log_frame(state, (uint64_t) frame_id, 0, 0, state->requested_mode, state->effective_mode,
		OUTCOME_SKIPPED_SUSPENDED, 0, 0, 0, 0, 0, 0, 0.0, 0.0, false, false, "not-requested", 0.0, NULL);
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
		drain_completed_frames_locked(state, false);
		if (!state->accepting || !state->ready)
		{
			log_failed_attempt_locked(state, (uint64_t) frame_id, width > 0 ? (NSUInteger) width : 0,
				height > 0 ? (NSUInteger) height : 0, (uint64_t) ui_generate_ns,
				(uint64_t) ui_generate_ns + monotonic_ns() - frame_start, OUTCOME_REJECTED, "renderer-not-ready");
			pthread_mutex_unlock(&state->mutex);
			return OUTCOME_REJECTED;
		}
		if (width <= 0 || height <= 0)
		{
			suspend_drawable_acquisition_locked(state);
			state->counters[COUNTER_SKIPPED_SUSPENDED]++;
			log_frame(state, (uint64_t) frame_id, 0, 0, state->requested_mode, state->effective_mode,
				OUTCOME_SKIPPED_SUSPENDED, 0, 0, 0, 0, monotonic_ns() - frame_start,
				0, 0.0, 0.0, false, false, "not-requested", 0.0, NULL);
			pthread_mutex_unlock(&state->mutex);
			return OUTCOME_SKIPPED_SUSPENDED;
		}
		update_drawable_extent_locked(state, (NSUInteger) width, (NSUInteger) height);
		id<MTLDevice> preferred = preferred_device(state);
		if (preferred != nil && preferred != state->device)
		{
			if (state->inflight != 0 || state->acquisition_pending)
			{
				state->counters[COUNTER_SKIPPED_IN_FLIGHT]++;
				log_frame(state, (uint64_t) frame_id, (NSUInteger) width, (NSUInteger) height,
					state->requested_mode, state->effective_mode, OUTCOME_SKIPPED_IN_FLIGHT,
					(uint64_t) ui_generate_ns, 0, 0, 0, (uint64_t) ui_generate_ns + monotonic_ns() - frame_start,
					0, 0.0, 0.0, false, false, "not-requested", 0.0, NULL);
				pthread_mutex_unlock(&state->mutex);
				return OUTCOME_SKIPPED_IN_FLIGHT;
			}
			release_ready_drawable_locked(state);
			state->acquisition_generation++;
			if (!build_device_objects_locked(state, preferred, @""))
			{
				state->ready = false;
				log_failed_attempt_locked(state, (uint64_t) frame_id, (NSUInteger) width, (NSUInteger) height,
					(uint64_t) ui_generate_ns, (uint64_t) ui_generate_ns + monotonic_ns() - frame_start,
					OUTCOME_ERROR, "device-rebuild-failed");
				pthread_mutex_unlock(&state->mutex);
				return OUTCOME_ERROR;
			}
			state->counters[COUNTER_DEVICE_REBUILDS]++;
			configure_present_mode_locked(state, state->requested_mode);
			schedule_drawable_acquisition_locked(state);
		}
		int slot_index = find_free_slot(state);
		if (slot_index < 0)
		{
			state->counters[COUNTER_SKIPPED_IN_FLIGHT]++;
			log_frame(state, (uint64_t) frame_id, (NSUInteger) width, (NSUInteger) height,
				state->requested_mode, state->effective_mode, OUTCOME_SKIPPED_IN_FLIGHT,
				(uint64_t) ui_generate_ns, 0, 0, 0, (uint64_t) ui_generate_ns + monotonic_ns() - frame_start,
				0, 0.0, 0.0, false, false, "not-requested", 0.0, NULL);
			pthread_mutex_unlock(&state->mutex);
			return OUTCOME_SKIPPED_IN_FLIGHT;
		}
		if (state->ready_drawable != nil &&
			(state->ready_drawable_width != (NSUInteger) width || state->ready_drawable_height != (NSUInteger) height ||
			state->ready_drawable.texture.width != (NSUInteger) width || state->ready_drawable.texture.height != (NSUInteger) height))
		{
			invalidate_drawable_acquisition_locked(state);
		}
		if (state->ready_drawable == nil)
		{
			bool acquisition_was_nil = state->acquisition_returned_nil;
			state->acquisition_returned_nil = false;
			schedule_drawable_acquisition_locked(state);
			if (!acquisition_was_nil) state->counters[COUNTER_SKIPPED_IN_FLIGHT]++;
			log_frame(state, (uint64_t) frame_id, (NSUInteger) width, (NSUInteger) height,
				state->requested_mode, state->effective_mode,
				acquisition_was_nil ? OUTCOME_NIL_DRAWABLE : OUTCOME_SKIPPED_IN_FLIGHT,
				(uint64_t) ui_generate_ns, 0, 0, 0, (uint64_t) ui_generate_ns + monotonic_ns() - frame_start,
				0, 0.0, 0.0, false, false, "not-requested", 0.0, NULL);
			pthread_mutex_unlock(&state->mutex);
			return acquisition_was_nil ? OUTCOME_NIL_DRAWABLE : OUTCOME_SKIPPED_IN_FLIGHT;
		}
		TextureSlot *slot = &state->slots[slot_index];
		slot->busy = true;
		if (!ensure_slot_texture_locked(state, slot_index, (NSUInteger) width, (NSUInteger) height))
		{
			slot->busy = false;
			log_failed_attempt_locked(state, (uint64_t) frame_id, (NSUInteger) width, (NSUInteger) height,
				(uint64_t) ui_generate_ns, (uint64_t) ui_generate_ns + monotonic_ns() - frame_start,
				OUTCOME_ERROR, "texture-rebuild-failed");
			pthread_mutex_unlock(&state->mutex);
			return OUTCOME_ERROR;
		}
		uint64_t upload_start = monotonic_ns();
		if (!upload_java_ui(env, state, ui_bytes, slot->texture, (NSUInteger) width, (NSUInteger) height))
		{
			slot->busy = false;
			log_failed_attempt_locked(state, (uint64_t) frame_id, (NSUInteger) width, (NSUInteger) height,
				(uint64_t) ui_generate_ns, (uint64_t) ui_generate_ns + monotonic_ns() - frame_start,
				OUTCOME_ERROR, "ui-upload-failed");
			pthread_mutex_unlock(&state->mutex);
			return OUTCOME_ERROR;
		}
		uint64_t upload_ns = monotonic_ns() - upload_start;
		state->counters[COUNTER_UI_UPLOAD_BYTES] += (uint64_t) width * (uint64_t) height * 4;
		id<CAMetalDrawable> drawable = state->ready_drawable;
		state->ready_drawable = nil;
		schedule_drawable_acquisition_locked(state);

		uint64_t encode_start = monotonic_ns();
		id<MTLCommandBuffer> command_buffer = [state->queue commandBuffer];
		if (command_buffer == nil)
		{
			slot->busy = false;
			[drawable release];
			track_release(state);
			state->counters[COUNTER_COMMAND_ERRORS]++;
			log_failed_attempt_locked(state, (uint64_t) frame_id, (NSUInteger) width, (NSUInteger) height,
				(uint64_t) ui_generate_ns, (uint64_t) ui_generate_ns + monotonic_ns() - frame_start,
				OUTCOME_ERROR, "command-buffer-unavailable");
			pthread_mutex_unlock(&state->mutex);
			return OUTCOME_ERROR;
		}
		if (!encode_scene(state, command_buffer, drawable.texture, slot->texture, (uint64_t) frame_id, false))
		{
			slot->busy = false;
			[drawable release];
			track_release(state);
			state->counters[COUNTER_COMMAND_ERRORS]++;
			log_failed_attempt_locked(state, (uint64_t) frame_id, (NSUInteger) width, (NSUInteger) height,
				(uint64_t) ui_generate_ns, (uint64_t) ui_generate_ns + monotonic_ns() - frame_start,
				OUTCOME_ERROR, "encoder-unavailable");
			pthread_mutex_unlock(&state->mutex);
			return OUTCOME_ERROR;
		}
		uint64_t encode_ns = monotonic_ns() - encode_start;

		FrameTiming *timing = calloc(1, sizeof(*timing));
		if (timing == NULL)
		{
			slot->busy = false;
			[drawable release];
			track_release(state);
			state->counters[COUNTER_INIT_ERRORS]++;
			log_failed_attempt_locked(state, (uint64_t) frame_id, (NSUInteger) width, (NSUInteger) height,
				(uint64_t) ui_generate_ns, (uint64_t) ui_generate_ns + monotonic_ns() - frame_start,
				OUTCOME_ERROR, "timing-allocation-failed");
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
		timing->drawable = drawable;
		bool callback_supported = !state->unsupported_present_callback &&
			[drawable respondsToSelector:@selector(addPresentedHandler:)] &&
			[drawable respondsToSelector:@selector(presentedTime)];
		if (state->timeout_present_callback)
		{
			timing->presentation_status = "pending";
			timing->presentation_token = [[RlhdPresentationToken alloc] init];
			track_create(state);
			callback_supported = false;
		}
		else if (state->dropped_present_callback)
		{
			timing->presentation_status = "dropped";
			state->counters[COUNTER_PRESENTATION_DROPPED]++;
			callback_supported = false;
		}
		else if (!callback_supported)
		{
			timing->presentation_status = "unsupported";
		}
		else
		{
			timing->presentation_status = "pending";
			timing->presentation_token = [[RlhdPresentationToken alloc] init];
			track_create(state);
		}
		state->pending_frame_callbacks++;
		state->inflight++;
		state->counters[COUNTER_SUBMITTED]++;
		state->counters[COUNTER_PRESENT_REQUESTED]++;
		if (state->inflight > state->counters[COUNTER_MAX_IN_FLIGHT])
		{
			state->counters[COUNTER_MAX_IN_FLIGHT] = state->inflight;
		}
		if (callback_supported)
		{
			RlhdPresentationToken *presentation_token = timing->presentation_token;
			[drawable addPresentedHandler:^(id<MTLDrawable> presented) {
				[presentation_token markPresentedTime:presented.presentedTime];
			}];
		}
		[command_buffer addCompletedHandler:^(id<MTLCommandBuffer> completed) {
			@autoreleasepool
			{
				pthread_mutex_lock(&state->mutex);
				timing->gpu_done = true;
				timing->gpu_start_time = completed.GPUStartTime;
				timing->gpu_end_time = completed.GPUEndTime;
				if (completed.status == MTLCommandBufferStatusError)
				{
					state->counters[COUNTER_COMMAND_ERRORS]++;
					timing->command_error = true;
				}
				state->counters[COUNTER_COMPLETED]++;
				state->slots[timing->slot_index].busy = false;
				if (state->inflight > 0) state->inflight--;
				timing->next = state->completed_frames;
				state->completed_frames = timing;
				if (state->pending_frame_callbacks > 0) state->pending_frame_callbacks--;
				drain_completed_frames_locked(state, false);
				pthread_cond_broadcast(&state->drained);
				pthread_mutex_unlock(&state->mutex);
			}
		}];
		uint64_t submit_start = monotonic_ns();
		timing->submit_host_ns = (uint64_t) (CACurrentMediaTime() * 1000000000.0);
		if (!state->bypass_present) [command_buffer presentDrawable:drawable];
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
	JNIEnv *env, jclass type, jlong handle, jbyteArray first_ui_bytes, jbyteArray second_ui_bytes)
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
		uint8_t first_result[8 * 8 * 4];
		uint8_t second_result[8 * 8 * 4];
		jbyteArray uploads[2] = {first_ui_bytes, second_ui_bytes};
		uint8_t *results[2] = {first_result, second_result};
		bool commands_valid = true;
		for (int pass_index = 0; pass_index < 2; pass_index++)
		{
			if (!upload_java_ui(env, state, uploads[pass_index], ui_texture, width, height))
			{
				commands_valid = false;
				break;
			}
			id<MTLCommandBuffer> command_buffer = [state->queue commandBuffer];
			if (command_buffer == nil || !encode_scene(state, command_buffer, render_texture, ui_texture,
				(uint64_t) (7 + pass_index), true))
			{
				commands_valid = false;
				break;
			}
			[command_buffer commit];
			[command_buffer waitUntilCompleted];
			if (command_buffer.status != MTLCommandBufferStatusCompleted)
			{
				commands_valid = false;
				break;
			}
			[render_texture getBytes:results[pass_index] bytesPerRow:width * 4
				fromRegion:MTLRegionMake2D(0, 0, width, height) mipmapLevel:0];
		}
		NSUInteger top_right = (1 * width + 6) * 4;
		NSUInteger bottom_left = (6 * width + 1) * 4;
		NSUInteger center = (3 * width + 3) * 4;
		NSUInteger animated = (6 * width + 6) * 4;
		bool valid = commands_valid &&
			channel_near(first_result[top_right], 26) && channel_near(first_result[top_right + 1], 19) &&
			channel_near(first_result[top_right + 2], 140) && channel_near(first_result[top_right + 3], 255) &&
			channel_near(first_result[bottom_left], 255) && channel_near(first_result[bottom_left + 1], 0) &&
			channel_near(first_result[bottom_left + 2], 0) && channel_near(first_result[bottom_left + 3], 255) &&
			first_result[center + 1] > 150 && first_result[center + 2] < 100 &&
			memcmp(&first_result[animated], &second_result[animated], 4) != 0;
		[render_texture release];
		[ui_texture release];
		track_release(state);
		track_release(state);
		pthread_mutex_unlock(&state->mutex);
		return valid ? JNI_TRUE : JNI_FALSE;
	}
}

JNIEXPORT void JNICALL Java_rs117_hd_spikes_macos_control_MetalControlNative_nativeClose(
	JNIEnv *env, jclass type, jlong handle, jlongArray final_counters)
{
	(void) type;
	@autoreleasepool
	{
		MetalControlState *state = state_from_handle(env, handle);
		if (state == NULL) return;
		if (final_counters == NULL || (*env)->GetArrayLength(env, final_counters) != COUNTER_COUNT)
		{
			throw_exception(env, "java/lang/IllegalArgumentException", "Final counter storage has the wrong length.");
			return;
		}
		jlong *counter_storage = (*env)->GetLongArrayElements(env, final_counters, NULL);
		if (counter_storage == NULL) return;
		pthread_mutex_lock(&state->mutex);
		if (!state->accepting)
		{
			pthread_mutex_unlock(&state->mutex);
			(*env)->ReleaseLongArrayElements(env, final_counters, counter_storage, JNI_ABORT);
			throw_exception(env, "java/lang/IllegalStateException", "Metal control renderer is already closing.");
			return;
		}
		if (state->fail_close_preconsume_once)
		{
			state->fail_close_preconsume_once = false;
			pthread_mutex_unlock(&state->mutex);
			(*env)->ReleaseLongArrayElements(env, final_counters, counter_storage, JNI_ABORT);
			throw_exception(env, "java/lang/IllegalStateException", "Injected pre-consumption close failure.");
			return;
		}
		state->accepting = false;
		state->acquisition_generation++;
		while (state->acquisition_pending || state->inflight != 0 || state->pending_frame_callbacks != 0)
		{
			pthread_cond_wait(&state->drained, &state->mutex);
		}
		drain_completed_frames_locked(state, true);
		state->ready = false;
		release_ready_drawable_locked(state);
		__block CAMetalLayer *layer = state->layer;
		__block id<MTLDevice> device = state->device;
		dispatch_main_sync(^{
			if (layer.device == device) layer.device = nil;
			[layer release];
		});
		release_device_objects_locked(state);
		state->layer = nil;
		track_release(state);
		if (state->drawable_queue != NULL)
		{
			dispatch_release(state->drawable_queue);
			state->drawable_queue = NULL;
			track_release(state);
		}
		track_release(state);
		bool has_errors = state->counters[COUNTER_INIT_ERRORS] != 0 ||
			state->counters[COUNTER_SHADER_ERRORS] != 0 || state->counters[COUNTER_PIPELINE_ERRORS] != 0 ||
			state->counters[COUNTER_COMMAND_ERRORS] != 0;
		if (state->log != NULL)
		{
			fprintf(state->log,
				"{\"schema\":\"rlhd.renderer.timing/v1\",\"type\":\"run_end\",\"backend\":\"metal-control\","
				"\"timestamp_ns\":%llu,\"requested_present_mode\":\"%s\",\"effective_present_mode\":\"%s\",\"counters\":",
				(unsigned long long) monotonic_ns(), present_mode_name(state->requested_mode), present_mode_name(state->effective_mode));
			write_counters(state->log, state->counters);
			fprintf(state->log, ",\"error\":%s}\n", has_errors ? "\"renderer-errors\"" : "null");
			fflush(state->log);
			fclose(state->log);
			state->log = NULL;
		}
		for (int index = 0; index < COUNTER_COUNT; index++) counter_storage[index] = (jlong) state->counters[index];
		pthread_mutex_unlock(&state->mutex);
		pthread_cond_destroy(&state->drained);
		pthread_mutex_destroy(&state->mutex);
		free(state);
		(*env)->ReleaseLongArrayElements(env, final_counters, counter_storage, 0);
	}
}
