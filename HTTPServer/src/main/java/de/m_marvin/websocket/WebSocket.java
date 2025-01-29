package de.m_marvin.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import de.m_marvin.simplelogging.Log;

/**
 * Stream based WebSocket implementation.<br>
 * Constructed by upgrading an existing Socket connection.<br>
 * Sufficient for both client and server side.
 * 
 * @author M_Marvin (Marvin Koehler)
 */
public class WebSocket {
	
	// NOTE: Trying to modify or "optimize" anything here can go wrong in ways, which might not immediately be noticeable
	
	private final Socket socket;
	
	// Receiving
	private final InputStream rxs;
	private final Thread receptor;
	private final PipedInputStream rxout;
	private long rxavailable = 0;
	private boolean textAvailable = false;
	private boolean frameIncomming = false;
	private boolean rxclosing = false;
	
	// Transmitting
	private final OutputStream txs;
	private final Thread transmitter;
	private final PipedOutputStream txin;
	private final int txframesize;
	private final boolean txmasking;
	private boolean txflush = false;
	private boolean frameOutgoing = false;
	private boolean textIncomming = false;
	private boolean txclosed = false;
	
	// Control
	private final Object txlock;
	private final Queue<ControlFrame> txcontrol = new ArrayDeque<WebSocket.ControlFrame>();
	private CompletableFuture<byte[]> pendingPing = null;
	private final boolean logverbose;
	private WebSocketCode closeCode = null;
	private byte[] closeReason = null;
	
	private static record ControlFrame(OPC op, byte[] data) {}
	
	private static enum OPC {
		CONTINUE(0x0),
		TEXT(0x1),
		BINARY(0x2),
		CLOSE(0x8),
		PING(0x9),
		PONG(0xA);
		
		private int opc;
		
		private OPC(int code) {
			this.opc = code;
		}
		
		public int opc() {
			return this.opc;
		}
		
		public static OPC of(int opc) {
			for (OPC e : values())
				if (e.opc() == opc) return e;
			return null;
		}
		
		public boolean control() {
			return this == CLOSE || this == PING || this == PONG;
		}
		
	}
	
	/**
	 * Constructs a new WebSocket by upgrading the supplied Socket.
	 * @param socket The underlying socket to use
	 * @param logverbose If interrupted connections without an clean WebSocket Close should be printed as errors to the logger
	 * @throws IOException If the Streams of the sockets could not be gathered
	 */
	public WebSocket(Socket socket, boolean logverbose) throws IOException {
		this(socket, 0x2800, false, logverbose);
	}
	
	/**
	 * Constructs a new WebSocket by upgrading the supplied Socket.
	 * @param socket The underlying socket to use
	 * @param framesize The frame size of transmitted packages, new fragments are sent if the buffer gets filled up to this ammount ot flush() is called
	 * @param masking If outgoing packages should be masked, has to be false for web-clients, but can be true for peer to peer connections
	 * @param logverbose If interrupted connections without an clean WebSocket Close should be printed as errors to the logger
	 * @throws IOException If the Streams of the sockets could not be gathered
	 */
	public WebSocket(Socket socket, int framesize, boolean masking, boolean logverbose) throws IOException {
		if (socket.isClosed())
			throw new IllegalStateException("Socket Closed!");
		this.socket = socket;
		this.rxs = this.socket.getInputStream();
		this.txs = this.socket.getOutputStream();
		this.rxout = new PipedInputStream() {
			@Override
			public synchronized int available() throws IOException {
				return (int) Math.min(WebSocket.this.rxavailable, Integer.MAX_VALUE);
			}
		};
		this.txin = new PipedOutputStream() {
			@Override
			public synchronized void flush() throws IOException {
				WebSocket.this.txflush = true;
				super.flush();
			}
		};
		var rxoutPipe = new PipedOutputStream(this.rxout);
		this.txframesize = framesize;
		this.txmasking = masking;
		var txinPipe = new PipedInputStream(this.txin, this.txframesize * 2);
		this.receptor = new Thread(() -> reception(rxoutPipe), "WebSocket-RX [" + this.socket.getInetAddress() + "]");
		this.transmitter = new Thread(() -> transmission(txinPipe), "WebSocket-TX [" + this.socket.getInetAddress() + "]");
		this.receptor.setDaemon(true);
		this.transmitter.setDaemon(true);
		this.receptor.start();
		this.transmitter.start();
		this.txlock = txinPipe;
		this.logverbose = logverbose;
	}

	// THESE VALUES WHERE SLEECTED BY WHAT SEEMED REASONABLE
	// THEY ARE NOT DOCUMENTED ANY WHERE RIGHT NOW
	public static final int MAX_STATUS_MESSAGE = 1024;
	public static final int MAX_PING_FRAME = Integer.MAX_VALUE;
	
	public static final SecureRandom MASK_RANDOM = new SecureRandom();
	
	private void reception(OutputStream rxout) {
		try {
			this.socket.setSoTimeout(0);
			txl: while (!this.rxclosing) {
				try {
					int frameStart = this.rxs.read();
					// Check RSVn Bits
					if ((frameStart & 0x70) > 0) {
						// Frame Error
						sendClose(WebSocketCode.PROTOCOL_ERROR, "RSVn bits non zero!".getBytes(StandardCharsets.UTF_8));
						break;
					}
					// Check OP Code
					OPC op = OPC.of(frameStart & 0xF);
					if (op == null) {
						// Frame Error
						sendClose(WebSocketCode.PROTOCOL_ERROR, "OP code invalid!".getBytes(StandardCharsets.UTF_8));
						break;
					}
					// Check FIN Bit
					boolean finalFragment = (frameStart & 0x80) > 0;
					
					// Read payload and masking
					int payload = this.rxs.read();
					boolean masked = (payload & 0x80) > 0;
					long payLen = payload & 0x7F;
					// Check for extended payload length
					if (payLen == 126) {
						payLen = this.rxs.read() << 8;
						payLen |= this.rxs.read() << 0;
					} else if (payLen == 127) {
						payLen =  (long) this.rxs.read() << 56;
						payLen |= (long) this.rxs.read() << 48;
						payLen |= (long) this.rxs.read() << 40;
						payLen |= (long) this.rxs.read() << 32;
						payLen |= (long) this.rxs.read() << 24;
						payLen |= (long) this.rxs.read() << 16;
						payLen |= (long) this.rxs.read() << 8;
						payLen |= (long) this.rxs.read() << 0;
					}
					// Check payload length
					if (payload < 0) {
						// Frame Error
						sendClose(WebSocketCode.MESSSAGE_TO_LONG, "payload to long!".getBytes(StandardCharsets.UTF_8));
						break;
					}
					// Read masking field
					int mask = 0;
					if (masked) {
						mask |= this.rxs.read() << 24;
						mask |= this.rxs.read() << 16;
						mask |= this.rxs.read() << 8;
						mask |= this.rxs.read() << 0;
					}
					
					// If control frame, prepate payload
					byte[] data = null;
					if (op.control()) {
						if (payLen >= MAX_PING_FRAME) {
							// Frame Error
							sendClose(WebSocketCode.MESSSAGE_TO_LONG, "control payload to long!".getBytes(StandardCharsets.UTF_8));
							break;
						}
						data = new byte[(int) payLen];
						for (int p = 0; p < payLen; p++) {
							int b = this.rxs.read();
							data[p] = (byte) (b ^ ((mask >> (3 - (p % 4)) * 8) & 0xFF));
						}
					}
					
					switch (op) {
					case CLOSE:
						// Check payload
						this.closeCode = null;
						if (data.length >= 2 && payLen <= MAX_STATUS_MESSAGE) {
							this.closeCode = WebSocketCode.of((((int) data[0]) << 8) | ((int) data[1]));
							if (this.closeCode != null) 
								this.closeReason = Arrays.copyOfRange(data, 2, data.length);
						}
						// Echo close if not previously send a close frame
						if (!this.txclosed) {
							sendClose(this.closeCode, this.closeReason);
						}
						this.rxclosing = true;
						// Call close listener
						if (this.closeListener != null) this.closeListener.onClose(this.closeCode, this.closeReason);
						continue;
					case PING:
						sendPong(data);
						continue;
					case PONG:
						if (this.pendingPing != null) {
							this.pendingPing.complete(data);
							this.pendingPing = null;
						}
						continue;
					case TEXT:
						if (this.frameIncomming) {
							// Frame Error
							sendClose(WebSocketCode.PROTOCOL_ERROR, "unexpected frame start!".getBytes(StandardCharsets.UTF_8));
							break txl;
						}
						this.frameIncomming = true;
						this.textAvailable = true;
						break;
					case BINARY:
						if (this.frameIncomming) {
							// Frame Error
							sendClose(WebSocketCode.PROTOCOL_ERROR, "unexpected frame start!".getBytes(StandardCharsets.UTF_8));
							break txl;
						}
						this.frameIncomming = true;
						this.textAvailable = false;
						break;
					case CONTINUE:
						if (!this.frameIncomming) {
							// Frame Error
							sendClose(WebSocketCode.PROTOCOL_ERROR, "unexpected continue!".getBytes(StandardCharsets.UTF_8));
							break txl;
						}
						break;
					}
					
					// Process payload
					this.rxavailable = payLen;
					long p = 0;
					while (p < payLen) {
						int b = this.rxs.read();
						if (masked) b = b ^ ((mask >> (3 - (p % 4)) * 8) & 0xFF);
						p++;
						rxout.write(b);
					}
					rxout.flush();
					
					// Terminate Frame if FIN
					if (finalFragment) {
						this.frameIncomming = false;
						// Call listener
						if (this.dataListener != null) this.dataListener.onText(this.rxout.available(), this.textIncomming);
					}
					
				} catch (SocketTimeoutException e) {
					// Frame Error
					sendClose(WebSocketCode.GENERIC_ERROR, "read frame timeout!".getBytes(StandardCharsets.UTF_8));
					break;
				}
				
			}
		} catch (IOException e) {
			// Frame Error
			sendClose(WebSocketCode.UNEXPECTED_ERROR, "unexpected reception error!".getBytes(StandardCharsets.UTF_8));
			if (this.logverbose) Log.defaultLogger().error("WebSocket RX IOExcpetion: Socket %s", this.socket.getInetAddress().toString(), e);
		} finally {
			try {
				rxout.close();
				if (this.txclosed) {
					this.socket.close();
				}
			} catch (IOException e) {}
			this.rxclosing = true;
		}
	}

	private void transmission(PipedInputStream txin) {
		try {
			synchronized (txin) {
				while (true) {
					// Wait for enough data for frame
					while (txin.available() < this.txframesize && !this.txflush && this.txcontrol.isEmpty()) {
						try { txin.wait(); } catch (InterruptedException e) {}
					}
					
					ControlFrame cf = null;
					if (txin.available() == 0 || (!this.txcontrol.isEmpty() && this.txcontrol.peek().op() != OPC.CLOSE)) {
						cf = this.txcontrol.poll();
					}
					
					// Prepare payload
					int payLen = 0;
					byte[] data = null;
					if (cf == null) {
						payLen = Math.min(this.txframesize, txin.available());
						data = txin.readNBytes(payLen);
					} else {
						payLen = cf.data().length;
						data = cf.data();
					}
					
					// Reset flush if buffer empty
					if (txin.available() == 0) this.txflush = false;
					
					// Mask payload
					int mask = 0;
					if (this.txmasking) {
						mask = MASK_RANDOM.nextInt();
						for (int p = 0; p < payLen; p++) {
							int b = data[p];
							data[p] = (byte) (b ^ ((mask >> (3 - (p % 4)) * 8) & 0xFF));
						}
					}
					
					// Prepare OPC, detect start of new frame
					OPC op = OPC.CONTINUE;
					if (cf != null) {
						op = cf.op();
					} else if (!this.frameOutgoing) {
						op = this.textIncomming ? OPC.TEXT : OPC.BINARY;
						if (!this.frameOutgoing) this.frameOutgoing = true;
					}
					
					// Detect last fragment by checking flush flag
					boolean finalFragment = cf != null || txin.available() == 0;
					
					// Reset final if buffer empty
					//if (cf == null && finalFragment) this.txfinal = false;
					
					// Send fragment start
					int frameStart = 0;
					if (finalFragment) frameStart |= 0x80;
					frameStart |= op.opc();
					this.txs.write(frameStart);
					
					// Send payload length
					int payload = this.txmasking ? 0x80 : 0x0;
					if (payLen < 126) {
						payload |= payLen;
						this.txs.write(payload);
					} else if (payLen < 0xFFFF) {
						payload |= 0x7E;
						this.txs.write(payload);
						this.txs.write((payLen >> 8) & 0xFF);
						this.txs.write((payLen >> 0) & 0xFF);
					} else {
						payload |= 0x7F;
						this.txs.write(payload);
						this.txs.write((payLen >> 56) & 0xFF);
						this.txs.write((payLen >> 48) & 0xFF);
						this.txs.write((payLen >> 40) & 0xFF);
						this.txs.write((payLen >> 32) & 0xFF);
						this.txs.write((payLen >> 24) & 0xFF);
						this.txs.write((payLen >> 16) & 0xFF);
						this.txs.write((payLen >> 8) & 0xFF);
						this.txs.write((payLen >> 0) & 0xFF);
					}
					
					// Send MASK
					if (this.txmasking) {
						this.txs.write((mask >> 24) & 0xFF);
						this.txs.write((mask >> 16) & 0xFF);
						this.txs.write((mask >> 8) & 0xFF);
						this.txs.write((mask >> 0) & 0xFF);
					}
					
					// Send payload
					this.txs.write(data);
					
					// Terminate current frame
					if (finalFragment) this.frameOutgoing = false;
					
					// Detect close frame
					if (op == OPC.CLOSE) break;
				}
			}
		} catch (IOException e) {
			if (this.logverbose) Log.defaultLogger().error("WebSocket TX IOExcpetion: Socket %s", this.socket.getInetAddress().toString(), e);
		} finally {
			try {
				txin.close();
				if (this.rxclosing) {
					this.socket.close();
				}
			} catch (IOException e) {}
			this.txclosed = true;
		}
	}
	
	/**
	 * Send ping and return an completable future which completes, if the pong is received.<br>
	 * A timeout should be applied to the future, to prevent it from blocking indefinitely if the ping got lost.
	 * @param data The data to use for the ping request
	 * @return A completable future which should complete with the sent data, mismatches are not detected.
	 */
	public CompletableFuture<byte[]> sendPing(byte[] data) {
		Objects.requireNonNull(data);
		if (!isOutputOpen()) return CompletableFuture.failedFuture(new IllegalStateException("WebSocket already closed!"));
		if (this.pendingPing == null) this.pendingPing = new CompletableFuture<byte[]>();
		this.txcontrol.add(new ControlFrame(OPC.PING, data));
		synchronized (this.txlock) {
			this.txlock.notifyAll();	
		}
		return this.pendingPing;
	}
	
	private void sendPong(byte[] data) {
		this.txcontrol.add(new ControlFrame(OPC.PONG, data));
		synchronized (this.txlock) {
			this.txlock.notifyAll();	
		}
	}
	
	/**
	 * This send a close frame to the other end and terminates the transmission of any future packages.<br>
	 * This does not close the actual socket immediately, since it is still required to receive the close frame from the other end.
	 * @param statusCode The status code for the close
	 * @param reason The reason for the closing, may be an UTF8 string or null
	 */
	public void sendClose(WebSocketCode statusCode, byte[] reason) {
		if (statusCode == null && reason != null && reason.length != 0)
			throw new IllegalStateException("Status Code has to be present if Response is present!");
		if (!isOutputOpen()) return;
		byte[] data = new byte[0];
		if (statusCode != null) {
			if (reason == null) reason = new byte[0];
			data = new byte[reason.length + 2];
			data[0] = (byte) ((statusCode.code() >> 8) & 0xFF);
			data[1] = (byte) ((statusCode.code() >> 0) & 0xFF);
			System.arraycopy(reason, 0, data, 2, reason.length);
		}
		this.txcontrol.add(new ControlFrame(OPC.CLOSE, data));
		synchronized (this.txlock) {
			this.txlock.notifyAll();	
		}
	}

	/**
	 * This send a close frame to the other end and terminates the transmission of any future packages.<br>
	 * This does not close the actual socket immediately, since it is still required to receive the close frame from the other end.
	 * @param statusCode The status code for the close
	 * @param reason The reason for the closing as an UTF8 string or null
	 */
	public void sendClose(WebSocketCode statusCode, String reason) {
		sendClose(statusCode, reason == null ? null : reason.getBytes(StandardCharsets.UTF_8));
	}
	
	/**
	 * This send a close frame to the other end and terminates the transmission of any future packages.<br>
	 * Waits for the close frame from the other end until the timeout is reached, which ever occurs first.<br>
	 * This does not close the actual socket immediately, since it is still required to receive the close frame from the other end.
	 * @param statusCode The status code for the close
	 * @param reason The reason for the closing, may be an UTF8 string or null
	 */
	public void sendCloseAndWait(WebSocketCode statusCode, byte[] reason, TimeUnit unit, int timeout) throws InterruptedException {
		sendClose(statusCode, reason);
		this.transmitter.join(unit.toMillis(timeout)); // Transmitter should instantly die after sending a close frame, but just to be safe, include an timeout here too.
		this.receptor.join(unit.toMillis(timeout));
	}

	/**
	 * This send a close frame to the other end and terminates the transmission of any future packages.<br>
	 * Waits for the close frame from the other end until the timeout is reached, which ever occurs first.<br>
	 * This does not close the actual socket immediately, since it is still required to receive the close frame from the other end.
	 * @param statusCode The status code for the close
	 * @param reason The reason for the closing as an UTF8 string or null
	 */
	public void sendCloseAndWait(WebSocketCode statusCode, String reason, TimeUnit unit, int timeout) throws InterruptedException {
		sendCloseAndWait(statusCode, reason == null ? null : reason.getBytes(StandardCharsets.UTF_8), unit, timeout);
	}

	/**
	 * This send a close frame to the other end and terminates the transmission of any future packages.<br>
	 * Waits for the close frame from the other end until the timeout is reached, which ever occurs first.<br>
	 * This does not close the actual socket immediately, since it is still required to receive the close frame from the other end.
	 * @param statusCode The status code for the close
	 * @param reason The reason for the closing, may be an UTF8 string or null
	 */
	public void closeSocket(WebSocketCode statusCode, byte[] reason, TimeUnit unit, int timeout) throws IOException, InterruptedException {
		sendCloseAndWait(statusCode, reason, unit, timeout);
		this.socket.close();
	}

	/**
	 * This send a close frame to the other end and terminates the transmission of any future packages.<br>
	 * Waits for the close frame from the other end until the timeout is reached, which ever occurs first.<br>
	 * This does not close the actual socket immediately, since it is still required to receive the close frame from the other end.
	 * @param statusCode The status code for the close
	 * @param reason The reason for the closing as an UTF8 string or null
	 */
	public void closeSocket(WebSocketCode statusCode, String reason, TimeUnit unit, int timeout) throws IOException, InterruptedException {
		closeSocket(statusCode, reason == null ? null : reason.getBytes(StandardCharsets.UTF_8), unit, timeout);
	}

	/**
	 * This send a close frame to the other end and terminates the transmission of any future packages.<br>
	 * Waits for the close frame from the other end until the timeout is reached, which ever occurs first.<br>
	 * This does not close the actual socket immediately, since it is still required to receive the close frame from the other end.
	 * @param statusCode The status code for the close
	 * @param reason The reason for the closing as an UTF8 string or null
	 */
	public void closeSocket(WebSocketCode statusCode, String reason) throws IOException, InterruptedException {
		closeSocket(statusCode, reason == null ? null : reason.getBytes(StandardCharsets.UTF_8), TimeUnit.SECONDS, 2);
	}
	
	/**
	 * @return true if and only if the last received frame contains textual data.
	 */
	public boolean holdsUTF8() {
		return this.textAvailable;
	}
	
	public InputStream getInputStream() {
		return this.rxout;
	}

	public boolean isInputOpen() {
		return !this.rxclosing;
	}
	
	/**
	 * Marks the future data-frames as UTF8 text frames, this takes only affect after the curren data-frame (if any) was terminated with markFinal()
	 * @param holdsUTF8 if the future data-frames should be marked as UTF8 text data
	 */
	public void markUTF8(boolean holdsUTF8) {
		this.textIncomming = holdsUTF8;
	}
	
	public OutputStream getOutputStream() {
		return this.txin;
	}
	
	public boolean isOutputOpen() {
		return !this.txclosed;
	}
	
	public int getSendFrameSize() {
		return this.txframesize;
	}

	public WebSocketCode getCloseCode() {
		return closeCode;
	}
	
	public byte[] getCloseReason() {
		return closeReason;
	}
	
	public String getCloseReasonUTF() {
		return this.closeReason == null ? null : new String(this.closeReason, StandardCharsets.UTF_8);
	}
	
	/**
	 * Utility method for sending binary data as one (potentially fragmented) terminated finalized package.
	 * @param text The data to send
	 * @throws IOException 
	 */
	public void sendBinary(byte[] data) throws IOException {
		getOutputStream().write(data);
		getOutputStream().flush();
	}
	
	/**
	 * Utility method for sending binary data as one (potentially fragmented) terminated finalized package.
	 * @param text The text to send
	 * @throws IOException 
	 */
	public void sendText(String text) throws IOException {
		markUTF8(true);
		sendBinary(text.getBytes(StandardCharsets.UTF_8));
	}
	
	/**
	 * Utility method for reading all binary data currently available in the buffer.
	 * @return Data currently in reception buffer.
	 * @throws IOException
	 */
	public byte[] readAvailableBinary() throws IOException {
		int available = getInputStream().available();
		return getInputStream().readNBytes(available);
	}
	
	/**
	 * Utility method for reading all text currently available in the buffer.
	 * @return Text currently in reception buffer.
	 * @throws IOException
	 */
	public String readAvailableText() throws IOException {
		int available = getInputStream().available();
		return new String(getInputStream().readNBytes(available), StandardCharsets.UTF_8);
	}
	
	/**
	 * Utility method for reading a single line terminated by LF or CRLF.
	 * @return A single line of text
	 * @throws IOException
	 */
	public String readLine() throws IOException {
		int i;
		StringBuffer buf = new StringBuffer();
		InputStreamReader reader = new InputStreamReader(getInputStream());
		while ((i = reader.read()) != -1) {
			if (i == '\n' || i == '\r') {
				if (i == '\r') reader.read(); // Discard trailing \n
				break;
			}
			buf.append((char) i);
		}
		return buf.toString();
	}

	/* Listener implementation, these are run on the reception thread, so no long blocking code in these */
	
	@FunctionalInterface
	public static interface CloseListener {
		public void onClose(WebSocketCode code, byte[] reason);
	}
	
	@FunctionalInterface
	public static interface DataListener {
		public void onText(int available, boolean isUTF);
	}
	
	private CloseListener closeListener;
	private DataListener dataListener;
	
	public void setCloseCode(WebSocketCode closeCode) {
		this.closeCode = closeCode;
	}
	
	public void setDataListener(DataListener dataListener) {
		this.dataListener = dataListener;
	}
	
}
