package de.m_marvin.httpserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import de.m_marvin.httpserver.server.HttpServer;
import de.m_marvin.httpserver.server.HttpsServer;

public class Test {
	
	public static final String HTML_FOLDER = "/web";
	
	public static void main(String[] args) throws URISyntaxException, InterruptedException {
		
		File runDir = new File(Test.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
		File certificateFile = new File(runDir, "../run/keystore.pfx");
		
		try {
			//HttpServer server = new HttpServer(80);
			HttpServer server = new HttpsServer(443, certificateFile, "password");
			server.setGetHandler(page -> {
				InputStream pageSource = Test.class.getResourceAsStream(HTML_FOLDER + page.getPath());
				return new ResponseInfo(pageSource == null ? HttpCode.NOT_FOUND : HttpCode.OK, pageSource == null ? "Not found!" : "OK", pageSource);
			});
			server.open();
			
			Thread.sleep(1000);
			
			server.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
}
