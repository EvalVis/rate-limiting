package com.evalvis.tests;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class ProcessPathSanitizer {

	private ProcessPathSanitizer() {
	}

	private static boolean isValidPathSegment(String segment) {
		try {
			Paths.get(segment);
			return true;
		}
		catch (InvalidPathException e) {
			return false;
		}
	}

	static void sanitizePath() {
		String raw = System.getenv("PATH");
		if (raw == null) {
			raw = System.getenv("Path");
		}
		if (raw == null) {
			return;
		}
		String sep = File.pathSeparator;
		List<String> kept = new ArrayList<>();
		for (String segment : raw.split(Pattern.quote(sep))) {
			if (segment == null) {
				continue;
			}
			String s = segment.trim();
			if (s.isEmpty()) {
				continue;
			}
			if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
				s = s.substring(1, s.length() - 1).trim();
			}
			if (s.isEmpty()) {
				continue;
			}
			String usable = s;
			if (!isValidPathSegment(usable) && usable.startsWith("?")) {
				usable = usable.substring(1);
			}
			if (!isValidPathSegment(usable)) {
				continue;
			}
			kept.add(usable);
		}
		if (kept.isEmpty()) {
			return;
		}
		String cleaned = String.join(sep, kept);
		if (cleaned.equals(raw)) {
			return;
		}
		putIntoProcessEnvironment("PATH", cleaned);
		putIntoProcessEnvironment("Path", cleaned);
	}

	private static void putIntoProcessEnvironment(String key, String value) {
		try {
			Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
			Object theEnvironment = getFieldValue(processEnvironmentClass, null, "theEnvironment");
			if (theEnvironment instanceof Map<?, ?> map) {
				@SuppressWarnings("unchecked")
				Map<String, String> cast = (Map<String, String>) map;
				cast.put(key, value);
			}
			Object theCaseInsensitiveEnvironment = getFieldValue(processEnvironmentClass, null, "theCaseInsensitiveEnvironment");
			if (theCaseInsensitiveEnvironment instanceof Map<?, ?> map) {
				@SuppressWarnings("unchecked")
				Map<String, String> cast = (Map<String, String>) map;
				cast.put(key, value);
			}
		}
		catch (ReflectiveOperationException ignored) {
		}
	}

	private static Object getFieldValue(Class<?> clazz, Object instance, String name) throws ReflectiveOperationException {
		java.lang.reflect.Field f = clazz.getDeclaredField(name);
		f.setAccessible(true);
		return f.get(instance);
	}

}
