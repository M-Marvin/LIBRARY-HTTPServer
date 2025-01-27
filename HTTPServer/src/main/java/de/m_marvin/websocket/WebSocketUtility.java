package de.m_marvin.websocket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

import de.m_marvin.http.HttpCode;
import de.m_marvin.http.ResponseInfo;
import de.m_marvin.simplelogging.Log;

public class WebSocketUtility {
	
	public static final String WS_UUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
	
	/**
	 * Utility method for testing if an HTTP request can be upgraded to an WebSocket connection.<br>
	 * Returns the required response info, when the status code equals 101, the connection can be upgraded after the response is processed by the client.
	 */
	public static ResponseInfo verifyUpgradeHttpSocket(Map<String, String> httpAttributes, String subProtocolUsed) {
		
		String upgrade = httpAttributes.get("Upgrade");
		String connection = httpAttributes.get("Connection");
		
		if (!"websocket".equals(upgrade) || !"Upgrade".equals(connection)) {
			return new ResponseInfo(HttpCode.UPGRADE_REQUIRED, "WebSocket Required", null);
		}
		
		String clientKey = httpAttributes.get("Sec-WebSocket-Key");
		String websockVer = httpAttributes.get("Sec-WebSocket-Version");
		String protocoll = httpAttributes.get("Sec-WebSocket-Protocol");
		
		if (!websockVer.equals("13")) {
			return new ResponseInfo(HttpCode.BAD_REQUEST, "WebSocket Version", null);
		}
		
		if (subProtocolUsed != null && (protocoll == null || !protocoll.contains(subProtocolUsed))) {
			return new ResponseInfo(HttpCode.NOT_IMPLEMENTED, "Protocol Not Supported", null);
		}
		
		String serverKey = null;
		try {
			serverKey = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((clientKey + WS_UUID).getBytes(StandardCharsets.US_ASCII)));
		} catch (NoSuchAlgorithmException e) {
			Log.defaultLogger().error("unable to process web socket handshake, missing SHA-1!", e);
			return new ResponseInfo(HttpCode.INTERNAL_SERVER_ERROR, "Internal Error", null);
		}
		
		return new ResponseInfo(HttpCode.SWITCHING_PROTOCOLS, "Switching Protocols", null)
				.addAdditionalInfo("Upgrade", upgrade)
				.addAdditionalInfo("Connection", connection)
				.addAdditionalInfo("Sec-WebSocket-Accept", serverKey)
				.addAdditionalInfo("Sec-WebSocket-Protocol", subProtocolUsed);
		
	}
	
}
