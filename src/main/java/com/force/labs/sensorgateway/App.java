package com.force.labs.sensorgateway;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.http.HttpSchemes;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.AbstractBuffer;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.google.gson.Gson;


/**
 * Hello world!
 *
 */
public class App extends HttpServlet {
	
	private Logger logger = Logger.getLogger(App.class.getName());
	
	private String sessionId;
	private String hostname;
	private Date lastRefresh;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    	Date twoHoursAgo = new Date(System.currentTimeMillis() - (120 * 60 * 1000));
        try {
        	if(sessionId == null || lastRefresh == null || lastRefresh.after(twoHoursAgo)) {
        		OAuthResponse response = getOAuthResponse();
        		sessionId = response.getSessionId();
        		hostname = response.getHostname();
        	}
        	
        	logger.info(sendRequest(createRequest(hostname + "/services/apexrest/gwestr/sensor", "application/json", req.getInputStream())));
        	
		} catch (Exception e) {
			resp.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
		}
    }
    
    protected OAuthResponse getOAuthResponse() throws Exception {
		return new Gson().fromJson(doSalesforceLogin(), OAuthResponse.class);
    }
    
    protected String doSalesforceLogin() throws Exception {
		lastRefresh = new Date(System.currentTimeMillis());
		
		String body = "grant_type=password" + "&" +
			"client_id=" + System.getenv("KEY") + "&" +
			"client_secret=" + System.getenv("SECRET") + "&" +
			"username=" + System.getenv("CRM_USER") + "&" +
			"password=" + System.getenv("CRM_PASSWORD") + System.getenv("TOKEN");
		
		return sendRequest(createRequest("https://login.salesforce.com/services/oauth2/token", "application/x-www-form-urlencoded", body));
    }
    
    protected ContentExchange createRequest(String URL, String contentType, String body) throws UnsupportedEncodingException {
		ContentExchange exchange = new ContentExchange();
		exchange.setMethod("POST");
		exchange.setURL(URL);
		exchange.setRequestHeader("Content-Type", contentType); 

		AbstractBuffer content = new ByteArrayBuffer(body.getBytes("UTF-8")); 
        exchange.setRequestContent(content); 
        exchange.setScheme(HttpSchemes.HTTPS_BUFFER);
        return exchange;
    }
    
    protected ContentExchange createRequest(String URL, String contentType, InputStream stream) {
		ContentExchange exchange = new ContentExchange();
		exchange.setMethod("POST");
		exchange.setURL(URL);
		exchange.setRequestHeader("Content-Type", contentType); 
		exchange.setRequestHeader("Authorization: ", "OAuth " + sessionId); 

        exchange.setRequestContentSource(stream);
        exchange.setScheme(HttpSchemes.HTTPS_BUFFER);
        return exchange;
    }
    
    protected String sendRequest(ContentExchange exchange) throws Exception {
		HttpClient client = new HttpClient();
		client.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
		client.setTimeout(30000);
		client.start();
		
		client.send(exchange);

		// block until we get the response
		int exchangeState = exchange.waitForDone();
		if (exchangeState == HttpExchange.STATUS_COMPLETED) {
			return exchange.getResponseContent();
		} else {
			throw new IllegalStateException("Waited and did not get a response from salesforce.");
		}
    }
    
    protected class OAuthResponse {
    	private String instance_url;
    	private String access_token;

		public String getSessionId() {
			return access_token;
		}
    	
    	public String getHostname() {
    		return instance_url;
    	}
    }

    public static void main(String[] args) throws Exception{
        Server server = new Server(Integer.valueOf(System.getenv("PORT")));
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/sensor");
        server.setHandler(context);
        context.addServlet(new ServletHolder(new App()),"/*");
        server.start();
        server.join();
    }
}
