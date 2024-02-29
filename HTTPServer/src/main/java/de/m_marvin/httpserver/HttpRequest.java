package de.m_marvin.httpserver;

/**
 * The possible request types to the server.
 * @author Marvin Koehler
 *
 */
public enum HttpRequest {
	
	GET("GET"),
	HEADER("HEADER"),
	PUT("PUT"),
	POST("POST"),
	PATCH("PATCH"),
	DELETE("DELETE"),
	TRACE("TRACE");
	
	private final String name;
	
	HttpRequest(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public static HttpRequest fromName(String requestName) {
		switch(requestName) {
		case "GET": return GET;
		case "HEADER": return HEADER;
		case "PUT": return PUT;
		case "POST": return POST;
		case "PATCH": return PATCH;
		case "DELETE": return DELETE;
		case "TRACE": return TRACE;
		default: return null;
		}
	}
	
}
