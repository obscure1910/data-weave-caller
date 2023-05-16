package de.codecentric.dwcaller.utils;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.mule.weave.v2.model.service.ReadFunctionProtocolHandler;
import org.mule.weave.v2.module.reader.SourceProvider;
import org.mule.weave.v2.parser.location.LocationCapable;

import scala.Option;

public class WeavePathProtocolHandler implements ReadFunctionProtocolHandler {
	private final String CLASSPATH_PREFIX = "classpath://";
	private PathBasedResourceResolver path;

	public WeavePathProtocolHandler(PathBasedResourceResolver path) {
		this.path = path;
	}

	@Override
	public boolean handles(String url) {
		return url.startsWith(CLASSPATH_PREFIX);
	}

	@Override
	public SourceProvider createSourceProvider(String url, LocationCapable locatable, Charset charset) {
		String uri = url.substring(CLASSPATH_PREFIX.length());
		Option<InputStream> maybeResource = path.resolve(uri);
		if (maybeResource.isDefined()) {
			return SourceProvider.apply(maybeResource.get(), charset);
		} else {
			return (SourceProvider) Option.empty();
		}
	}
	
}
