package de.m_marvin.httpserver;

public enum HttpCode {
	
	OK("OK", 200),
	BAD_REQUEST("BAD_REQUEST", 400),
	NOT_FOUND("NOT_FOUND", 404),
	LENGHT_REQUIRED("LENGTH_REQUIRED", 411),
	NOT_SUPPORTED("NOT_SUPPORTED", 501);
	
	
	private final String name;
	private final int code;
	
	private HttpCode(String name, int code) {
		this.name = name;
		this.code = code;
	}
	
	public int getCode() {
		return code;
	}
	
	public String getName() {
		return name;
	}
	
}
