package com.ccp.implementations.instant.messenger.telegram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.ccp.constantes.CcpOtherConstants;
import com.ccp.decorators.CcpJsonRepresentation;
import com.ccp.decorators.CcpStringDecorator;
import com.ccp.dependency.injection.CcpDependencyInjection;
import com.ccp.especifications.http.CcpHttpHandler;
import com.ccp.especifications.http.CcpHttpRequester;
import com.ccp.especifications.http.CcpHttpResponseType;
import com.ccp.especifications.instant.messenger.CcpInstantMessenger;
import com.ccp.exceptions.db.instant.messenger.CcpInstantMessengerChatErrorCount;
import com.ccp.exceptions.instant.messenger.CcpInstantMessageThisBotWasBlockedByThisUser;
import com.ccp.exceptions.instant.messenger.CcpTooManyRequests;
import com.ccp.http.CcpHttpMethods;
import com.ccp.process.CcpThrowException;

class TelegramInstantMessenger implements CcpInstantMessenger {
	
	public Long getMembersCount(CcpJsonRepresentation parameters) {
		CcpHttpRequester ccpHttp = CcpDependencyInjection.getDependency(CcpHttpRequester.class);

		Long chatId = parameters.getAsLongNumber("chatId");
		String url = this.getCompleteUrl(parameters);
		ccpHttp.executeHttpRequest(url + "/getChatMemberCount?chat_id=" + chatId, CcpHttpMethods.GET, CcpOtherConstants.EMPTY_JSON, "", 200);
		CcpHttpHandler ccpHttpHandler = new CcpHttpHandler(200);

		CcpJsonRepresentation response = ccpHttpHandler.executeHttpSimplifiedGet("getMembersCount", url, CcpHttpResponseType.singleRecord);
		if(response.getAsBoolean("ok") == false) {
			throw new CcpInstantMessengerChatErrorCount(chatId);
		}
		Long result = response.getAsLongNumber("result");
		return result;
	}
	CcpInstantMessenger throwThisBotWasBlockedByThisUser(String token) {
		throw new CcpInstantMessageThisBotWasBlockedByThisUser(token);
	}
	
	CcpInstantMessenger throwTooManyRequests() {
		throw new CcpTooManyRequests();
	}
	public CcpJsonRepresentation sendMessage(CcpJsonRepresentation json) {
		String token = this.getToken(json);
		
//		this.throwTooManyRequests();
//		this.throwThisBotWasBlockedByThisUser(token);
		Long chatId = json.getAsLongNumber("recipient");
		Set<String> fieldSet = json.fieldSet();
		for (String fieldName : fieldSet) {
			Object value = json.get(fieldName);
			if(value instanceof Collection<?>) {
				json = json.put(fieldName, value.toString());
			}
		}
		String message = json.getAsString("message")
				.replace("\u003cBR\u003e", "\n")
				.replace("\u003cbr\u003e", "\n")
				.replace("<BR/>", "\n")
				.replace("<br/>", "\n")
				.replace("<BR>", "\n")
				.replace("<br>", "\n")
				.replace("u003c", "(")
				.replace("u003e", ")")
				.replace("<p>", "\n")
				.replace("</p>", " ")
				.replace("<", "(")
				.replace(">", ")")
				;
		Long replyTo =  json.getOrDefault("replyTo", 0L);

		if(message.trim().isEmpty()) {
			return CcpOtherConstants.EMPTY_JSON;
		}

		List<String> texts = new ArrayList<>();
		int length = message.length();
		int pieces = length / 4096;
		
		for(int k = 0; k <= pieces; k++) {
			int nextBound = (k + 1) * 4096;
			int currentBound = k * 4096;
			String text = message.substring(currentBound, nextBound > length ? length : nextBound);
			texts.add(text);
		}
		String botUrl = this.getCompleteUrl(json);
		String url = botUrl + "/sendMessage";
		CcpHttpMethods method = CcpHttpMethods.valueOf( json.getAsString("method"));
		
		CcpJsonRepresentation handlers = CcpOtherConstants.EMPTY_JSON
				.addJsonTransformer("403", new CcpThrowException(new CcpInstantMessageThisBotWasBlockedByThisUser(token)))
				.addJsonTransformer("429", new CcpThrowException(new CcpTooManyRequests()))
				.addJsonTransformer("200", CcpOtherConstants.DO_NOTHING)
				;
		
		CcpHttpHandler ccpHttpHandler = new CcpHttpHandler(handlers);
		
		for (String text : texts) {
			CcpJsonRepresentation body = CcpOtherConstants.EMPTY_JSON
					.put("reply_to_message_id", replyTo)
					.put("parse_mode", "html")
					.put("chat_id", chatId)
					.put("text", text);
			
			CcpJsonRepresentation response = ccpHttpHandler.executeHttpRequest("sendInstantMessage", url, method, CcpOtherConstants.EMPTY_JSON, body, CcpHttpResponseType.singleRecord);
			
			CcpJsonRepresentation result = response.getInnerJson("result");
			
			replyTo = result.getAsLongNumber("message_id");
		}
		
		return CcpOtherConstants.EMPTY_JSON
//				.put("token", token)
				;
	}


	private String getCompleteUrl(CcpJsonRepresentation parameters) {
		CcpJsonRepresentation properties = new CcpStringDecorator("application_properties").propertiesFrom().environmentVariablesOrClassLoaderOrFile();	
		String tokenValue = this.getToken(parameters);
		
		String urlKey = parameters.getAsString("url");
		String urlValue = properties.getAsString(urlKey);
		
		return urlValue + tokenValue;
	}

	public String getToken(CcpJsonRepresentation parameters) {
		CcpJsonRepresentation properties = new CcpStringDecorator("application_properties").propertiesFrom().environmentVariablesOrClassLoaderOrFile();	
		String tokenKey = parameters.getAsString("token");
		String tokenValue = properties.getAsString(tokenKey);
		if(tokenValue.trim().isEmpty()) {
			return tokenKey;
		}
		return tokenValue;
	}


	
	public String getFileName(CcpJsonRepresentation parameters) {
		
//		CcpMapDecorator messageData = parameters.getInternalMap("messageData");
//		String botToken = parameters.getAsString("botToken");
		
		return "";
	}

	
	public String extractTextFromMessage(CcpJsonRepresentation parameters) {
//		CcpMapDecorator messageData = parameters.getInternalMap("messageData");
//		String botToken = parameters.getAsString("botToken");

		return "";
	}

	
}
