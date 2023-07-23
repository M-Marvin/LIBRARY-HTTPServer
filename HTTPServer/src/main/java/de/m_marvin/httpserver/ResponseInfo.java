package de.m_marvin.httpserver;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ResponseInfo {
	
	protected HttpCode responseCode;
	protected String responseMessage;
	protected Map<String, String> additionalInfo = new HashMap<>();
	protected Optional<InputStream> contentSource;
	
	public ResponseInfo(HttpCode code, String message, InputStream contentSource) {
		this.responseCode = code;
		this.responseMessage = message;
		this.contentSource = Optional.ofNullable(contentSource);
	}

	public void addAdditionalInfo(String key, Object value) {
		this.additionalInfo.put(key, value.toString());
	}
	
	public HttpCode getResponseCode() {
		return responseCode;
	}
	
	public String getResponseMessage() {
		return responseMessage;
	}
	
	public Map<String, String> getAdditionalInfo() {
		return additionalInfo;
	}
	
	public Optional<InputStream> getContentSource() {
		return contentSource;
	}
	
}
