package rs117.hd.spikes.macos;

import java.awt.Canvas;

interface NativeSurfaceAccess
{
	long create();

	void attach(long stateHandle, Canvas canvas);

	void resize(long stateHandle, SurfaceExtent extent);

	long layerHandle(long stateHandle);

	void detach(long stateHandle);

	void close(long stateHandle);
}
