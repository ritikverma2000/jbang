package dev.jbang.source.resolvers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.ResourceResolver;
import dev.jbang.util.Util;

/**
 * A <code>ResourceResolver</code> that, when given a resource string which
 * looks like a classpath URL, will look up the referenced resource and, if it's
 * a file on the local file system return a direct reference to that file or if
 * it's a JAR resource it will create a copy of it in the cache and return a
 * reference to that.
 */
public class ClasspathResourceResolver implements ResourceResolver {
	@Override
	public ResourceRef resolve(String resource) {
		ResourceRef result = null;

		if (resource.startsWith("classpath:/")) {
			result = getClasspathResource(resource);
		}

		return result;
	}

	private static ResourceRef getClasspathResource(String cpResource) {
		String ref = cpResource.substring(11);
		Util.verboseMsg("Duplicating classpath resource " + ref);
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		if (cl == null) {
			cl = ResourceRef.class.getClassLoader();
		}
		URL url = cl.getResource(ref);
		if (url == null) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Resource not found on class path: " + ref);
		}

		try {
			File f = new File(url.toURI());
			if (f.canRead()) {
				return ResourceRef.forCachedResource(cpResource, f);
			}
		} catch (URISyntaxException | IllegalArgumentException e) {
			// Ignore
		}

		// We couldn't read the file directly from the class path so let's make a copy
		try (InputStream is = url.openStream()) {
			Path to = Util.getUrlCache(cpResource);
			Files.createDirectories(to.getParent());
			Files.copy(is, to, StandardCopyOption.REPLACE_EXISTING);
			return ResourceRef.forCachedResource(cpResource, to.toFile());
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR,
					"Resource could not be copied from class path: " + ref, e);
		}
	}
}