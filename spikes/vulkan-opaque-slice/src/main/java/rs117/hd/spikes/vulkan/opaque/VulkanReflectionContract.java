package rs117.hd.spikes.vulkan.opaque;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class VulkanReflectionContract {
	private VulkanReflectionContract() {}

	public static void main(String[] paths) throws IOException {
		if (paths.length != 4) fail("Expected reflection paths for exactly four shaders");
		Map<String, String> reflections = new LinkedHashMap<>();
		for (String value : paths) {
			Path path = Paths.get(value);
			String fileName = path.getFileName().toString();
			String shader = fileName.substring(0, fileName.length() - ".reflect.json".length());
			reflections.put(shader, new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
		}
		validateAll(reflections);
	}

	public static void validateAll(Map<String, String> reflections) {
		requireKeys(reflections.keySet(), set("opaque.vert", "opaque.frag", "ui.vert", "ui.frag"), "shader set");
		validateOpaqueVertex(parse(reflections.get("opaque.vert")));
		validateStage(parse(reflections.get("opaque.frag")), "frag",
			interfaces(item("linearColor", "vec3", 0)), interfaces(item("outputColor", "vec4", 0)),
			set("entryPoints", "inputs", "outputs"));
		validateStage(parse(reflections.get("ui.vert")), "vert", Collections.emptyMap(),
			interfaces(item("textureCoordinate", "vec2", 0)), set("entryPoints", "types", "outputs"));
		validateUiFragment(parse(reflections.get("ui.frag")));
	}

	private static void validateOpaqueVertex(JsonObject root) {
		requireKeys(root.keySet(), set("entryPoints", "types", "inputs", "outputs", "ssbos", "push_constants"), "opaque.vert root");
		validateEntry(root, "vert");
		validateInterfaces(root, "inputs", interfaces(
			item("packedPosition", "ivec4", 0), item("packedUvw", "vec4", 1),
			item("packedNormal", "ivec4", 2), item("faceRef", "int", 3)));
		validateInterfaces(root, "outputs", interfaces(item("linearColor", "vec3", 0)));

		JsonArray ssbos = array(root, "ssbos", "opaque.vert");
		if (ssbos.size() != 1) fail("opaque.vert must expose exactly one SSBO");
		JsonObject ssbo = object(ssbos.get(0), "opaque.vert SSBO");
		requireKeys(ssbo.keySet(), set("type", "name", "readonly", "block_size", "set", "binding"), "opaque.vert SSBO");
		requireString(ssbo, "name", "FaceMetadata");
		requireBoolean(ssbo, "readonly", true);
		requireNumber(ssbo, "block_size", 0);
		requireNumber(ssbo, "set", 0);
		requireNumber(ssbo, "binding", 0);

		JsonArray pushes = array(root, "push_constants", "opaque.vert");
		if (pushes.size() != 1) fail("opaque.vert must expose exactly one push block");
		JsonObject push = object(pushes.get(0), "opaque.vert push block");
		requireKeys(push.keySet(), set("type", "name", "push_constant", "block_size"), "opaque.vert push block");
		requireString(push, "name", "pushConstants");
		requireBoolean(push, "push_constant", true);
		requireNumber(push, "block_size", 72);

		JsonObject types = object(root.get("types"), "opaque.vert types");
		JsonObject metadataType = object(types.get(string(ssbo, "type")), "FaceMetadata type");
		requireKeys(metadataType.keySet(), set("name", "members"), "FaceMetadata type");
		requireString(metadataType, "name", "FaceMetadata");
		JsonArray metadataMembers = array(metadataType, "members", "FaceMetadata");
		if (metadataMembers.size() != 1) fail("FaceMetadata must contain exactly one runtime scalar array");
		JsonObject words = object(metadataMembers.get(0), "FaceMetadata.words");
		requireKeys(words.keySet(), set("name", "type", "array", "array_size_is_literal", "offset", "array_stride"), "FaceMetadata.words");
		requireString(words, "name", "words");
		requireString(words, "type", "int");
		requireSingleNumber(words, "array", 0);
		requireSingleBoolean(words, "array_size_is_literal", true);
		requireNumber(words, "offset", 0);
		requireNumber(words, "array_stride", 4);

		JsonObject pushType = object(types.get(string(push, "type")), "PushConstants type");
		requireKeys(pushType.keySet(), set("name", "members"), "PushConstants type");
		requireString(pushType, "name", "PushConstants");
		Map<String, JsonObject> members = membersByName(pushType, "PushConstants");
		requireKeys(members.keySet(), set("clipFromWorld", "sceneBase"), "PushConstants members");
		JsonObject matrix = members.get("clipFromWorld");
		requireKeys(matrix.keySet(), set("name", "type", "offset", "matrix_stride"), "clipFromWorld");
		requireString(matrix, "type", "mat4");
		requireNumber(matrix, "offset", 0);
		requireNumber(matrix, "matrix_stride", 16);
		JsonObject sceneBase = members.get("sceneBase");
		requireKeys(sceneBase.keySet(), set("name", "type", "offset"), "sceneBase");
		requireString(sceneBase, "type", "ivec2");
		requireNumber(sceneBase, "offset", 64);
	}

	private static void validateUiFragment(JsonObject root) {
		requireKeys(root.keySet(), set("entryPoints", "inputs", "outputs", "textures"), "ui.frag root");
		validateEntry(root, "frag");
		validateInterfaces(root, "inputs", interfaces(item("textureCoordinate", "vec2", 0)));
		validateInterfaces(root, "outputs", interfaces(item("outputColor", "vec4", 0)));
		JsonArray textures = array(root, "textures", "ui.frag");
		if (textures.size() != 1) fail("ui.frag must expose exactly one sampler");
		JsonObject sampler = object(textures.get(0), "ui.frag sampler");
		requireKeys(sampler.keySet(), set("type", "name", "set", "binding"), "ui.frag sampler");
		requireString(sampler, "type", "sampler2D");
		requireString(sampler, "name", "uiTexture");
		requireNumber(sampler, "set", 0);
		requireNumber(sampler, "binding", 0);
	}

	private static void validateStage(JsonObject root, String stage, Map<String, Interface> inputs,
		Map<String, Interface> outputs, Set<String> keys) {
		requireKeys(root.keySet(), keys, stage + " root");
		validateEntry(root, stage);
		validateInterfaces(root, "inputs", inputs);
		validateInterfaces(root, "outputs", outputs);
	}

	private static void validateEntry(JsonObject root, String stage) {
		JsonArray entries = array(root, "entryPoints", stage);
		if (entries.size() != 1) fail(stage + " must expose exactly one entry point");
		JsonObject entry = object(entries.get(0), stage + " entry point");
		requireKeys(entry.keySet(), set("name", "mode"), stage + " entry point");
		requireString(entry, "name", "main");
		requireString(entry, "mode", stage);
	}

	private static void validateInterfaces(JsonObject root, String key, Map<String, Interface> expected) {
		if (!root.has(key)) {
			if (expected.isEmpty()) return;
			fail(key + " are missing");
		}
		Map<String, Interface> actual = new LinkedHashMap<>();
		for (JsonElement element : array(root, key, key)) {
			JsonObject variable = object(element, key + " item");
			requireKeys(variable.keySet(), set("type", "name", "location"), key + " item");
			String name = string(variable, "name");
			Interface iface = new Interface(string(variable, "type"), integer(variable, "location"));
			if (actual.put(name, iface) != null) fail(key + " contains duplicate " + name);
		}
		if (!actual.equals(expected)) fail(key + " mismatch: expected " + expected + " but was " + actual);
	}

	private static Map<String, JsonObject> membersByName(JsonObject type, String context) {
		Map<String, JsonObject> result = new LinkedHashMap<>();
		for (JsonElement element : array(type, "members", context)) {
			JsonObject member = object(element, context + " member");
			String name = string(member, "name");
			if (result.put(name, member) != null) fail(context + " contains duplicate member " + name);
		}
		return result;
	}

	private static JsonObject parse(String json) {
		if (json == null) fail("Reflection JSON is missing");
		try {
			return object(JsonParser.parseString(json), "reflection root");
		} catch (RuntimeException failure) {
			throw new IllegalArgumentException("Invalid reflection JSON", failure);
		}
	}

	private static JsonObject object(JsonElement value, String context) {
		if (value == null || !value.isJsonObject()) fail(context + " must be an object");
		return value.getAsJsonObject();
	}

	private static JsonArray array(JsonObject object, String key, String context) {
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonArray()) fail(context + " " + key + " must be an array");
		return value.getAsJsonArray();
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) fail(key + " must be a string");
		return value.getAsString();
	}

	private static int integer(JsonObject object, String key) {
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) fail(key + " must be a number");
		return value.getAsInt();
	}

	private static void requireKeys(Set<String> actual, Set<String> expected, String context) {
		if (!actual.equals(expected)) fail(context + " keys mismatch: expected " + expected + " but was " + actual);
	}

	private static void requireString(JsonObject object, String key, String expected) {
		String actual = string(object, key);
		if (!actual.equals(expected)) fail(key + " must be " + expected + " but was " + actual);
	}

	private static void requireBoolean(JsonObject object, String key, boolean expected) {
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean() || value.getAsBoolean() != expected)
			fail(key + " must be " + expected);
	}

	private static void requireNumber(JsonObject object, String key, int expected) {
		if (integer(object, key) != expected) fail(key + " must be " + expected);
	}

	private static void requireSingleNumber(JsonObject object, String key, int expected) {
		JsonArray values = array(object, key, key);
		if (values.size() != 1 || values.get(0).getAsInt() != expected) fail(key + " must contain only " + expected);
	}

	private static void requireSingleBoolean(JsonObject object, String key, boolean expected) {
		JsonArray values = array(object, key, key);
		if (values.size() != 1 || values.get(0).getAsBoolean() != expected) fail(key + " must contain only " + expected);
	}

	private static Interface item(String name, String type, int location) { return new Interface(name, type, location); }
	private static Map<String, Interface> interfaces(Interface... values) {
		Map<String, Interface> result = new LinkedHashMap<>();
		for (Interface value : values) result.put(value.name, new Interface(value.type, value.location));
		return result;
	}
	private static Set<String> set(String... values) { return new LinkedHashSet<>(Arrays.asList(values)); }
	private static void fail(String message) { throw new IllegalArgumentException(message); }

	private static final class Interface {
		final String name;
		final String type;
		final int location;
		Interface(String name, String type, int location) { this.name = name; this.type = type; this.location = location; }
		Interface(String type, int location) { this(null, type, location); }
		@Override public boolean equals(Object other) {
			return other instanceof Interface && type.equals(((Interface) other).type) && location == ((Interface) other).location;
		}
		@Override public int hashCode() { return type.hashCode() * 31 + location; }
		@Override public String toString() { return type + "@" + location; }
	}
}
