#import <Cocoa/Cocoa.h>
#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>
#import <QuartzCore/CATransaction.h>
#import <dispatch/dispatch.h>
#import <jawt_md.h>
#import <jni.h>

#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "surface_state.h"

typedef struct MacMetalSurfaceState
{
	pthread_mutex_t mutex;
	RlhdSurfaceLifecycle lifecycle;
	id<JAWT_SurfaceLayers> surface_layers;
	CAMetalLayer *metal_layer;
} MacMetalSurfaceState;

static void throw_exception(JNIEnv *env, const char *class_name, const char *message)
{
	jclass exception_class = (*env)->FindClass(env, class_name);
	if (exception_class != NULL)
	{
		(*env)->ThrowNew(env, exception_class, message);
	}
}

static MacMetalSurfaceState *state_from_handle(JNIEnv *env, jlong handle)
{
	if (handle == 0)
	{
		throw_exception(env, "java/lang/IllegalStateException", "Native surface state is unavailable.");
		return NULL;
	}
	return (MacMetalSurfaceState *) (intptr_t) handle;
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

static id<JAWT_SurfaceLayers> acquire_surface_layers(JNIEnv *env, jobject canvas)
{
	JAWT awt;
	memset(&awt, 0, sizeof(awt));
	awt.version = JAWT_VERSION_1_4 | JAWT_MACOSX_USE_CALAYER;
	if (JAWT_GetAWT(env, &awt) == JNI_FALSE)
	{
		throw_exception(env, "java/lang/IllegalStateException", "JAWT_GetAWT failed for the Canvas.");
		return nil;
	}

	JAWT_DrawingSurface *drawing_surface = awt.GetDrawingSurface(env, canvas);
	if (drawing_surface == NULL)
	{
		throw_exception(env, "java/lang/IllegalStateException", "JAWT could not acquire the Canvas drawing surface.");
		return nil;
	}

	id<JAWT_SurfaceLayers> surface_layers = nil;
	jint lock_result = drawing_surface->Lock(drawing_surface);
	if ((lock_result & JAWT_LOCK_ERROR) == 0)
	{
		JAWT_DrawingSurfaceInfo *surface_info = drawing_surface->GetDrawingSurfaceInfo(drawing_surface);
		if (surface_info != NULL)
		{
			if (surface_info->platformInfo != NULL)
			{
				surface_layers = (id<JAWT_SurfaceLayers>) [(id) surface_info->platformInfo retain];
			}
			drawing_surface->FreeDrawingSurfaceInfo(surface_info);
		}
		drawing_surface->Unlock(drawing_surface);
	}
	awt.FreeDrawingSurface(drawing_surface);

	if (surface_layers == nil)
	{
		throw_exception(env, "java/lang/IllegalStateException", "JAWT did not provide macOS surface layers.");
	}
	return surface_layers;
}

static bool release_attached_objects(MacMetalSurfaceState *state)
{
	if (state->surface_layers == nil && state->metal_layer == nil)
	{
		return true;
	}
	__block bool cleared = false;
	dispatch_main_sync(^{
		CALayer *sentinel = [[CALayer alloc] init];
		sentinel.hidden = YES;
		sentinel.bounds = CGRectZero;
		sentinel.frame = CGRectZero;
		sentinel.actions = @{
			@"bounds": [NSNull null],
			@"contents": [NSNull null],
			@"position": [NSNull null]
		};
		[CATransaction begin];
		[CATransaction setDisableActions:YES];
		state->surface_layers.layer = sentinel;
		CALayer *installed_layer = state->surface_layers.layer;
		cleared = installed_layer == sentinel && ![installed_layer isKindOfClass:[CAMetalLayer class]];
		if (cleared)
		{
			[sentinel removeFromSuperlayer];
		}
		[CATransaction commit];
		[sentinel release];
		if (!cleared)
		{
			return;
		}
		[state->metal_layer release];
		[(id) state->surface_layers release];
		state->metal_layer = nil;
		state->surface_layers = nil;
	});
	return cleared;
}

JNIEXPORT jlong JNICALL Java_rs117_hd_spikes_macos_MacMetalSurfaceNative_nativeCreate(JNIEnv *env, jclass type)
{
	(void) type;
	@autoreleasepool
	{
		MacMetalSurfaceState *state = calloc(1, sizeof(*state));
		if (state == NULL)
		{
			throw_exception(env, "java/lang/OutOfMemoryError", "Unable to allocate native surface state.");
			return 0;
		}
		if (pthread_mutex_init(&state->mutex, NULL) != 0)
		{
			free(state);
			throw_exception(env, "java/lang/IllegalStateException", "Unable to initialize native surface serialization.");
			return 0;
		}
		rlhd_surface_initialize(&state->lifecycle);
		return (jlong) (intptr_t) state;
	}
}

JNIEXPORT void JNICALL Java_rs117_hd_spikes_macos_MacMetalSurfaceNative_nativeAttach(JNIEnv *env, jclass type, jlong handle, jobject canvas)
{
	(void) type;
	@autoreleasepool
	{
		MacMetalSurfaceState *state = state_from_handle(env, handle);
		if (state == NULL)
		{
			return;
		}
		pthread_mutex_lock(&state->mutex);
		if (state->lifecycle.phase != RLHD_SURFACE_DETACHED)
		{
			pthread_mutex_unlock(&state->mutex);
			throw_exception(env, "java/lang/IllegalStateException", "Native surface is not detached.");
			return;
		}

		id<JAWT_SurfaceLayers> surface_layers = acquire_surface_layers(env, canvas);
		if (surface_layers == nil)
		{
			pthread_mutex_unlock(&state->mutex);
			return;
		}

		__block CAMetalLayer *metal_layer = nil;
		dispatch_main_sync(^{
			metal_layer = [[CAMetalLayer alloc] init];
			metal_layer.pixelFormat = MTLPixelFormatBGRA8Unorm;
			metal_layer.contentsScale = 1.0;
			metal_layer.drawableSize = CGSizeZero;
			metal_layer.actions = @{
				@"bounds": [NSNull null],
				@"contents": [NSNull null],
				@"contentsScale": [NSNull null],
				@"drawableSize": [NSNull null],
				@"position": [NSNull null]
			};
			[CATransaction begin];
			[CATransaction setDisableActions:YES];
			surface_layers.layer = metal_layer;
			[CATransaction commit];
		});

		if (metal_layer == nil)
		{
			dispatch_main_sync(^{ [(id) surface_layers release]; });
			pthread_mutex_unlock(&state->mutex);
			throw_exception(env, "java/lang/IllegalStateException", "Unable to create CAMetalLayer.");
			return;
		}
		state->surface_layers = surface_layers;
		state->metal_layer = metal_layer;
		(void) rlhd_surface_attach(&state->lifecycle);
		pthread_mutex_unlock(&state->mutex);
	}
}

JNIEXPORT void JNICALL Java_rs117_hd_spikes_macos_MacMetalSurfaceNative_nativeResize(JNIEnv *env, jclass type, jlong handle, jint logical_width, jint logical_height, jdouble backing_scale)
{
	(void) type;
	@autoreleasepool
	{
		MacMetalSurfaceState *state = state_from_handle(env, handle);
		if (state == NULL)
		{
			return;
		}
		pthread_mutex_lock(&state->mutex);
		RlhdSurfaceResult result = rlhd_surface_resize(&state->lifecycle, logical_width, logical_height, backing_scale);
		if (result != RLHD_SURFACE_OK)
		{
			pthread_mutex_unlock(&state->mutex);
			throw_exception(env, "java/lang/IllegalStateException", result == RLHD_SURFACE_INVALID_EXTENT ?
				"Native surface extent is invalid." : "Native surface is not attached.");
			return;
		}
		dispatch_main_sync(^{
			[CATransaction begin];
			[CATransaction setDisableActions:YES];
			state->metal_layer.bounds = CGRectMake(0.0, 0.0, logical_width, logical_height);
			state->metal_layer.contentsScale = backing_scale;
			state->metal_layer.drawableSize = CGSizeMake(logical_width * backing_scale, logical_height * backing_scale);
			[CATransaction commit];
		});
		pthread_mutex_unlock(&state->mutex);
	}
}

JNIEXPORT jlong JNICALL Java_rs117_hd_spikes_macos_MacMetalSurfaceNative_nativeLayerHandle(JNIEnv *env, jclass type, jlong handle)
{
	(void) type;
	@autoreleasepool
	{
		MacMetalSurfaceState *state = state_from_handle(env, handle);
		if (state == NULL)
		{
			return 0;
		}
		pthread_mutex_lock(&state->mutex);
		if (state->lifecycle.phase != RLHD_SURFACE_ATTACHED || state->metal_layer == nil)
		{
			pthread_mutex_unlock(&state->mutex);
			throw_exception(env, "java/lang/IllegalStateException", "Native surface is not attached.");
			return 0;
		}
		jlong layer_handle = (jlong) (intptr_t) state->metal_layer;
		pthread_mutex_unlock(&state->mutex);
		return layer_handle;
	}
}

JNIEXPORT void JNICALL Java_rs117_hd_spikes_macos_MacMetalSurfaceNative_nativeDetach(JNIEnv *env, jclass type, jlong handle)
{
	(void) type;
	@autoreleasepool
	{
		MacMetalSurfaceState *state = state_from_handle(env, handle);
		if (state == NULL)
		{
			return;
		}
		pthread_mutex_lock(&state->mutex);
		if (state->lifecycle.phase != RLHD_SURFACE_ATTACHED)
		{
			pthread_mutex_unlock(&state->mutex);
			throw_exception(env, "java/lang/IllegalStateException", "Native surface is not attached.");
			return;
		}
		if (!release_attached_objects(state))
		{
			pthread_mutex_unlock(&state->mutex);
			throw_exception(env, "java/lang/IllegalStateException", "JAWT surface layer could not be replaced by a detach sentinel.");
			return;
		}
		(void) rlhd_surface_detach(&state->lifecycle);
		pthread_mutex_unlock(&state->mutex);
	}
}

JNIEXPORT void JNICALL Java_rs117_hd_spikes_macos_MacMetalSurfaceNative_nativeClose(JNIEnv *env, jclass type, jlong handle)
{
	(void) type;
	@autoreleasepool
	{
		MacMetalSurfaceState *state = state_from_handle(env, handle);
		if (state == NULL)
		{
			return;
		}
		pthread_mutex_lock(&state->mutex);
		if (state->lifecycle.phase == RLHD_SURFACE_CLOSED)
		{
			pthread_mutex_unlock(&state->mutex);
			throw_exception(env, "java/lang/IllegalStateException", "Native surface is already closed.");
			return;
		}
		if (state->lifecycle.phase == RLHD_SURFACE_ATTACHED)
		{
			if (!release_attached_objects(state))
			{
				pthread_mutex_unlock(&state->mutex);
				throw_exception(env, "java/lang/IllegalStateException", "JAWT surface layer could not be replaced by a detach sentinel.");
				return;
			}
		}
		(void) rlhd_surface_close(&state->lifecycle);
		pthread_mutex_unlock(&state->mutex);
		pthread_mutex_destroy(&state->mutex);
		free(state);
	}
}
