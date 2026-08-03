#ifndef RLHD_MAC_SURFACE_STATE_H
#define RLHD_MAC_SURFACE_STATE_H

#include <stdbool.h>

typedef enum RlhdSurfacePhase
{
	RLHD_SURFACE_DETACHED,
	RLHD_SURFACE_ATTACHED,
	RLHD_SURFACE_CLOSED
} RlhdSurfacePhase;

typedef enum RlhdSurfaceResult
{
	RLHD_SURFACE_OK,
	RLHD_SURFACE_INVALID_STATE,
	RLHD_SURFACE_INVALID_EXTENT
} RlhdSurfaceResult;

typedef struct RlhdSurfaceLifecycle
{
	RlhdSurfacePhase phase;
	int logical_width;
	int logical_height;
	double pixel_width;
	double pixel_height;
	double backing_scale;
	bool suspended;
} RlhdSurfaceLifecycle;

void rlhd_surface_initialize(RlhdSurfaceLifecycle *state);
RlhdSurfaceResult rlhd_surface_attach(RlhdSurfaceLifecycle *state);
RlhdSurfaceResult rlhd_surface_resize(RlhdSurfaceLifecycle *state, int logical_width, int logical_height, double backing_scale);
RlhdSurfaceResult rlhd_surface_detach(RlhdSurfaceLifecycle *state);
RlhdSurfaceResult rlhd_surface_close(RlhdSurfaceLifecycle *state);

#endif
