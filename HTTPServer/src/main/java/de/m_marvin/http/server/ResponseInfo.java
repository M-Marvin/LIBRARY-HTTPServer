package de.m_marvin.http.server;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import de.m_marvin.http.HttpCode;

public class ResponseInfo {
	
	protected HttpCode responseCode;
	protected String responseMessage;
	protected Map<String, String> attributes = new LinkedHashMap<>();
	protected Optional<InputStream> contentSource;
	protected boolean keepSocket = false;
	
	public ResponseInfo(HttpCode code, String message, InputStream contentSource) {
		this.responseCode = code;
		this.responseMessage = message;
		this.contentSource = Optional.ofNullable(contentSource);
	}

	public ResponseInfo addAdditionalInfo(String key, Object value) {
		this.attributes.put(key, value.toString());
		return this;
	}
	
	public ResponseInfo keepSocket() {
		this.keepSocket = true;
		return this;
	}
	
	public HttpCode getResponseCode() {
		return responseCode;
	}
	
	public String getResponseMessage() {
		return responseMessage;
	}
	
	public Map<String, String> getAttributes() {
		return attributes;
	}
	
	public Optional<InputStream> getContentSource() {
		return contentSource;
	}
	
	public boolean isKeepSocket() {
		return keepSocket;
	}
	
}
