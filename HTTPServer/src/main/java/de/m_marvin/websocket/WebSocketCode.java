package de.m_marvin.websocket;

public enum WebSocketCode {
	
	CLOSE_NORMALY(1000),
	GOING_AWAY(1001),
	PROTOCOL_ERROR(1002),
	CAN_NOT_ACCEPT(1003),
	NO_STATUS_CODE(1005),
	CLOSED_ABNORMAL(1006),
	MESSAGE_INCONSISTENT(1007),
	GENERIC_ERROR(1008),
	MESSSAGE_TO_LONG(1009),
	EXPECTED_EXTENSION(1010),
	UNEXPECTED_ERROR(1011),
	TLS_FAILED(1015);
	
	private int code;
	
	private WebSocketCode(int code) {
		this.code = code;
	}
	
	public int code() {
		return code;
	}

	public static WebSocketCode of(int code) {
		for (WebSocketCode e : values())
			if (e.code() == code) return e;
		return null;
	}
	
}
