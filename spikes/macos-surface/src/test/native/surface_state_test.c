#include "surface_state.h"

#include <assert.h>
#include <math.h>
#include <stdio.h>

int main(void)
{
	RlhdSurfaceLifecycle state;
	rlhd_surface_initialize(&state);
	assert(state.phase == RLHD_SURFACE_DETACHED);
	assert(state.suspended);

	for (int cycle = 0; cycle < 100; cycle++)
	{
		assert(rlhd_surface_attach(&state) == RLHD_SURFACE_OK);
		assert(rlhd_surface_attach(&state) == RLHD_SURFACE_INVALID_STATE);
		assert(rlhd_surface_resize(&state, 321 + cycle, 181 + cycle, 1.5) == RLHD_SURFACE_OK);
		assert(fabs(state.pixel_width - ((321 + cycle) * 1.5)) < 0.000001);
		assert(fabs(state.pixel_height - ((181 + cycle) * 1.5)) < 0.000001);
		assert(!state.suspended);
		assert(rlhd_surface_resize(&state, 0, 181, 2.0) == RLHD_SURFACE_OK);
		assert(state.suspended);
		assert(rlhd_surface_resize(&state, 640, 360, 2.0) == RLHD_SURFACE_OK);
		assert(state.pixel_width == 1280.0);
		assert(state.pixel_height == 720.0);
		assert(!state.suspended);
		assert(rlhd_surface_detach(&state) == RLHD_SURFACE_OK);
		assert(rlhd_surface_detach(&state) == RLHD_SURFACE_INVALID_STATE);
	}

	assert(rlhd_surface_attach(&state) == RLHD_SURFACE_OK);
	assert(rlhd_surface_resize(&state, -1, 1, 1.0) == RLHD_SURFACE_INVALID_EXTENT);
	assert(rlhd_surface_resize(&state, 1, 1, NAN) == RLHD_SURFACE_INVALID_EXTENT);
	assert(rlhd_surface_close(&state) == RLHD_SURFACE_OK);
	assert(rlhd_surface_attach(&state) == RLHD_SURFACE_INVALID_STATE);
	assert(rlhd_surface_resize(&state, 1, 1, 1.0) == RLHD_SURFACE_INVALID_STATE);
	assert(rlhd_surface_detach(&state) == RLHD_SURFACE_INVALID_STATE);
	assert(rlhd_surface_close(&state) == RLHD_SURFACE_INVALID_STATE);

	puts("surface_state_test: 100 lifecycle cycles passed");
	return 0;
}
