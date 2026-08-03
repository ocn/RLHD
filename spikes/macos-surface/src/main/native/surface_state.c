#include "surface_state.h"

#include <math.h>

static void reset_extent(RlhdSurfaceLifecycle *state)
{
	state->logical_width = 0;
	state->logical_height = 0;
	state->pixel_width = 0.0;
	state->pixel_height = 0.0;
	state->backing_scale = 1.0;
	state->suspended = true;
}

void rlhd_surface_initialize(RlhdSurfaceLifecycle *state)
{
	state->phase = RLHD_SURFACE_DETACHED;
	reset_extent(state);
}

RlhdSurfaceResult rlhd_surface_attach(RlhdSurfaceLifecycle *state)
{
	if (state->phase != RLHD_SURFACE_DETACHED)
	{
		return RLHD_SURFACE_INVALID_STATE;
	}
	state->phase = RLHD_SURFACE_ATTACHED;
	reset_extent(state);
	return RLHD_SURFACE_OK;
}

RlhdSurfaceResult rlhd_surface_resize(RlhdSurfaceLifecycle *state, int logical_width, int logical_height, double backing_scale)
{
	if (state->phase != RLHD_SURFACE_ATTACHED)
	{
		return RLHD_SURFACE_INVALID_STATE;
	}
	double pixel_width = logical_width * backing_scale;
	double pixel_height = logical_height * backing_scale;
	if (logical_width < 0 || logical_height < 0 || !isfinite(backing_scale) || backing_scale <= 0.0 ||
		!isfinite(pixel_width) || !isfinite(pixel_height))
	{
		return RLHD_SURFACE_INVALID_EXTENT;
	}
	state->logical_width = logical_width;
	state->logical_height = logical_height;
	state->pixel_width = pixel_width;
	state->pixel_height = pixel_height;
	state->backing_scale = backing_scale;
	state->suspended = logical_width == 0 || logical_height == 0;
	return RLHD_SURFACE_OK;
}

RlhdSurfaceResult rlhd_surface_detach(RlhdSurfaceLifecycle *state)
{
	if (state->phase != RLHD_SURFACE_ATTACHED)
	{
		return RLHD_SURFACE_INVALID_STATE;
	}
	state->phase = RLHD_SURFACE_DETACHED;
	reset_extent(state);
	return RLHD_SURFACE_OK;
}

RlhdSurfaceResult rlhd_surface_close(RlhdSurfaceLifecycle *state)
{
	if (state->phase == RLHD_SURFACE_CLOSED)
	{
		return RLHD_SURFACE_INVALID_STATE;
	}
	state->phase = RLHD_SURFACE_CLOSED;
	reset_extent(state);
	return RLHD_SURFACE_OK;
}
