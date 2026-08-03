package rs117.hd.spikes.vulkan.opaque;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class VulkanProductionIsolation {
	private VulkanProductionIsolation() {}

	public static void main(String[] paths) throws IOException {
		if (paths.length == 0) throw new IllegalArgumentException("Expected the production JAR path");
		validateProductionJar(Paths.get(paths[0]));
		List<String> forbidden = new ArrayList<>();
		for (int index = 1; index < paths.length; index++)
			if (isForbiddenDependencyName(Paths.get(paths[index]).getFileName().toString())) forbidden.add(paths[index]);
		if (!forbidden.isEmpty()) throw new IllegalStateException("Renderer/native dependencies leaked into production: " + forbidden);
	}

	public static boolean isForbiddenDependencyName(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.contains("lwjgl") || lower.contains("vulkan") || lower.contains("moltenvk") ||
			lower.matches(".*(^|[-_.])natives?([-_.].*|$)") || hasNativeExtension(lower);
	}

	public static void validateProductionJar(Path jar) throws IOException {
		List<String> forbidden = new ArrayList<>();
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				String name = entries.nextElement().getName();
				String lower = name.toLowerCase(Locale.ROOT);
				if (lower.startsWith("rs117/hd/spikes/vulkan/opaque/") ||
					lower.startsWith("shaders/vulkan-opaque-slice/") || lower.contains("org/lwjgl/") ||
					lower.contains("moltenvk") || hasNativeExtension(lower)) forbidden.add(name);
			}
		}
		if (!forbidden.isEmpty()) throw new IllegalStateException("Renderer/native entries leaked into production JAR: " + forbidden);
	}

	private static boolean hasNativeExtension(String name) {
		return name.endsWith(".dylib") || name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".jnilib");
	}
}
