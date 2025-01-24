package de.m_marvin.httpserver;

/**
 * The possible status codes send from the server.
 * @author Marvin Koehler
 *
 */

public enum HttpCode {
	
	CONTINUE("Continue", 100),
	SWITCHING_PROTOCOLLS("Switching Protocolls", 101),
	PROCESSING("Processing", 102),
	EARLY_HINTS("Early Hints", 103),
	
	OK("OK", 200),
	CREATED("Created", 201),
	ACCEPTED("Accepted", 202),
	NON_AUTHORITATIVE_INFORMATION("Non-Authoritative Information", 203),
	NO_CONTENT("No Content", 204),
	RESET_CONTENT("Reset Content", 205),
	PARTIAL_CONTENT("Partial Content", 206),
	MULTI_STATUS("Multi-Status", 207),
	ALREADY_REPORTED("Akready Reported", 208),
	IM_USED("IM Used", 226),
	
	MULTIPLE_CHOICES("Multiple Choices", 300),
	MOVED_PERMANENTLY("Moved Permanently", 301),
	FOUND("Found", 302),
	SEE_OTHER("See Other", 303),
	NOT_MODIFIED("Not Modified", 304),
	USE_PROXY("Use Proxy", 305),
	SWITCH_PROXY("Switch Proxy", 306),
	TEMPORARY_REDIRECT("Temporary Redirect", 307),
	PERMANENT_REDIRECT("Permanent Redirect", 308),
	
	BAD_REQUEST("BAD_REQUEST", 400),
	UNAUTHORIZED("Unauthorized", 401),
	PAYMENT_REQUIRED("Payment Required", 402),
	FORBIDDEN("Forbidden", 403),
	NOT_FOUND("NOT_FOUND", 404),
	METHOD_NOT_ALLOWED("Method Not Allowed", 405),
	NOT_ACCEPTABLE("Not Acceptable", 406),
	PROXY_AUTHENTICATION_REQUIRED("Proxy Authentication Required", 407),
	REQUEST_TIMEOUT("Request Timeout", 408),
	CONFLICT("Conflict", 409),
	GONE("Gone", 410),
	LENGHT_REQUIRED("Length Required", 411),
	PRECONDITIONS_FAILED("Preconditions Failed", 412),
	PAYLOAD_TOO_LARGE("Payload Too Large", 413),
	URI_TOO_LONG("URI Too Long", 414),
	UNSUPPORTED_MEDIA_TYPE("Unsupported Media Type", 415),
	RANGE_NOT_SATISFIABLE("Range Not Satisfiable", 416),
	EXPECTATION_FAILED("Expectation Failed", 417),
	IM_A_TEAPOT("I'm a teapot!", 418), // Yeah, wee need this :D
	MISDIRECTED_REQUEST("Missdirected Request", 421),
	UNPROCESSABLE_CONTENT("Unprocessable Content", 422),
	LOCKED("Locked", 423),
	FAILED_DEPENDENCY("Failed Dependency", 424),
	TOO_EARLY("Too Early", 425),
	UPGRADE_REQUIRED("Upgrade Required", 426),
	PRECONDITION_REQUIRED("Precondition Required", 428),
	TOO_MANY_REQUESTS("Too Many Requests", 429),
	REQUEST_HEADER_FIELDS_TOO_LARGE("Request Header Fields Too Large", 431),
	UNAVAILABLE_FOR_LEGAL_REASONS("Unavailable For Legal Reasons", 451),
	
	INTERNAL_SERVER_ERROR("Internal Server Error", 500),
	NOT_IMPLEMENTED("Not Implemented", 501),
	BAD_GATEWAY("Bad Gateway", 502),
	SERVICE_UNAVAILABLE("Service Unavailable", 503),
	GATEWAY_TIMEOUT("Gateway Timeout", 504),
	HTTP_VERSION_NOT_SUPPORTED("HTTP Version Not Supported", 505),
	VARIANT_ALSO_NEGOTIATES("Variant Also Negotiates", 506),
	INSUFFICIENT_STORAGE("Insufficient Storage", 507),
	LOOP_DETECTED("Loop Detected", 508),
	NOT_EXTENDED("Not Extended", 510),
	NETWORK_AUTHENTICATION_REQUIRED("Network Authentication Required", 511);
	
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
