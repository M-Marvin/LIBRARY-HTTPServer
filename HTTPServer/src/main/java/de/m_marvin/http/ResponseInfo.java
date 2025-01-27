package de.m_marvin.http;

import java.io.InputStream;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ResponseInfo {
	
	protected HttpCode responseCode;
	protected String responseMessage;
	protected Map<String, String> attributes = new LinkedHashMap<>();
	protected Optional<InputStream> contentSource;
	protected CompletableFuture<Socket> keepSocket = null;
	
	public ResponseInfo(HttpCode code, String message, InputStream contentSource) {
		this.responseCode = code;
		this.responseMessage = message;
		this.contentSource = Optional.ofNullable(contentSource);
	}

	public ResponseInfo addAdditionalInfo(String key, Object value) {
		this.attributes.put(key, value.toString());
		return this;
	}
	
	public CompletableFuture<Socket> keepSocket() {
		this.keepSocket = new CompletableFuture<Socket>();
		return this.keepSocket;
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
	
	public boolean freeSocket(Socket socket) {
		if (this.keepSocket == null) return true;
		this.keepSocket.complete(socket);
		return false;
	}
	
}
