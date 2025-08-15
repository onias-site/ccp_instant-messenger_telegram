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
import com.ccp.exceptions.db.instant.messenger.CcpErrorInstantMessengerChatErrorCount;
import com.ccp.exceptions.instant.messenger.CcpErrorInstantMessageThisBotWasBlockedByThisUser;
import com.ccp.exceptions.instant.messenger.CcpErrorInstantMessageTooManyRequests;
import com.ccp.http.CcpHttpMethods;
import com.ccp.process.CcpFunctionThrowException;
enum TelegramInstantMessengerConstants{
	chatId, ok, result, recipient, message, method, replyTo, reply_to_message_id, parse_mode, chat_id, text, url, message_id, token
}
class TelegramInstantMessenger implements CcpInstantMessenger {
	
	public Long getMembersCount(CcpJsonRepresentation parameters) {
		CcpHttpRequester ccpHttp = CcpDependencyInjection.getDependency(CcpHttpRequester.class);

		Long chatId = parameters.getAsLongNumber(TelegramInstantMessengerConstants.chatId);
		String url = this.getCompleteUrl(parameters);
		ccpHttp.executeHttpRequest(url + "/getChatMemberCount?chat_id=" + chatId, CcpHttpMethods.GET, CcpOtherConstants.EMPTY_JSON, "", 200);
		CcpHttpHandler ccpHttpHandler = new CcpHttpHandler(200);

		CcpJsonRepresentation response = ccpHttpHandler.executeHttpSimplifiedGet("getMembersCount", url, CcpHttpResponseType.singleRecord);
		if(response.getAsBoolean(TelegramInstantMessengerConstants.ok) == false) {
			throw new CcpErrorInstantMessengerChatErrorCount(chatId);
		}
		Long result = response.getAsLongNumber(TelegramInstantMessengerConstants.result);
		return result;
	}
	CcpInstantMessenger throwThisBotWasBlockedByThisUser(String token) {
		throw new CcpErrorInstantMessageThisBotWasBlockedByThisUser(token);
	}
	
	CcpInstantMessenger throwTooManyRequests() {
		throw new CcpErrorInstantMessageTooManyRequests();
	}
	public CcpJsonRepresentation sendMessage(CcpJsonRepresentation json) {
		String token = this.getToken(json);
		
//		this.throwTooManyRequests();
//		this.throwThisBotWasBlockedByThisUser(token);
		Long chatId = json.getAsLongNumber(TelegramInstantMessengerConstants.recipient);
		Set<String> fieldSet = json.fieldSet();
		for (String fieldName : fieldSet) {
			Object value = json.get(fieldName);
			if(value instanceof Collection<?>) {
				json = json.put(fieldName, value.toString());
			}
		}
		String message = json.getAsString(TelegramInstantMessengerConstants.message)
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
		Long replyTo =  json.getOrDefault(TelegramInstantMessengerConstants.replyTo, 0L);

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
		CcpHttpMethods method = CcpHttpMethods.valueOf( json.getAsString(TelegramInstantMessengerConstants.method));
		
		CcpJsonRepresentation handlers = CcpOtherConstants.EMPTY_JSON
				.addJsonTransformer(403, new CcpFunctionThrowException(new CcpErrorInstantMessageThisBotWasBlockedByThisUser(token)))
				.addJsonTransformer(429, new CcpFunctionThrowException(new CcpErrorInstantMessageTooManyRequests()))
				.addJsonTransformer(200, CcpOtherConstants.DO_NOTHING)
				;
		
		CcpHttpHandler ccpHttpHandler = new CcpHttpHandler(handlers);
		
		for (String text : texts) {
			CcpJsonRepresentation body = CcpOtherConstants.EMPTY_JSON
					.put(TelegramInstantMessengerConstants.reply_to_message_id, replyTo)
					.put(TelegramInstantMessengerConstants.parse_mode, "html")
					.put(TelegramInstantMessengerConstants.chat_id, chatId)
					.put(TelegramInstantMessengerConstants.text, text);
			
			CcpJsonRepresentation response = ccpHttpHandler.executeHttpRequest("sendInstantMessage", url, method, CcpOtherConstants.EMPTY_JSON, body, CcpHttpResponseType.singleRecord);
			
			CcpJsonRepresentation result = response.getInnerJson(TelegramInstantMessengerConstants.result);
			
			replyTo = result.getAsLongNumber(TelegramInstantMessengerConstants.message_id);
		}
		
		return CcpOtherConstants.EMPTY_JSON
//				.put("token", token)
				;
	}


	private String getCompleteUrl(CcpJsonRepresentation parameters) {
		CcpJsonRepresentation properties = new CcpStringDecorator("application_properties").propertiesFrom().environmentVariablesOrClassLoaderOrFile();	
		String tokenValue = this.getToken(parameters);
		
		String urlKey = parameters.getAsString(TelegramInstantMessengerConstants.url);
		String urlValue = properties.getAsString(urlKey);
		
		return urlValue + tokenValue;
	}

	public String getToken(CcpJsonRepresentation parameters) {
		CcpJsonRepresentation properties = new CcpStringDecorator("application_properties").propertiesFrom().environmentVariablesOrClassLoaderOrFile();	
		String tokenKey = parameters.getAsString(TelegramInstantMessengerConstants.token);
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
