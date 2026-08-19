package com.ccp.implementations.instant.messenger.telegram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ccp.constants.CcpOtherConstants;
import com.ccp.decorators.CcpJsonRepresentation;
import com.ccp.decorators.CcpJsonRepresentation.CcpJsonFieldName;
import com.ccp.decorators.CcpStringDecorator;
import com.ccp.especifications.http.CcpHttpBodyBinary;
import com.ccp.especifications.http.CcpHttpBodyText;
import com.ccp.especifications.http.CcpHttpContentType;
import com.ccp.especifications.http.CcpHttpHandler;
import com.ccp.especifications.http.CcpHttpMethods;
import com.ccp.especifications.http.CcpHttpResponseType;

import com.ccp.especifications.http.CcpHttpTooManyRequests;
import com.ccp.especifications.instant.messenger.CcpErrorInstantMessageThisBotWasBlockedByThisUser;
import com.ccp.especifications.instant.messenger.CcpInstantMessenger;
import com.ccp.process.CcpFunctionThrowException;
/**
 * Implementação de {@code CcpInstantMessenger} para o Telegram. Envia mensagens de texto
 * (suportando paginação automática a cada 4096 caracteres) e arquivos via multipart.
 * Trata erros HTTP 403 (bot bloqueado) e 429 (muitas requisições) lançando as exceções
 * correspondentes.
 */
class TelegramInstantMessenger implements CcpInstantMessenger {
	enum JsonFieldNames implements CcpJsonFieldName{
		chatId, ok, result, recipient, message, method, replyTo, reply_to_message_id, parse_mode, chat_id, text, url, message_id, token, urlInstantMessengerKey, fileName, caption
	}
	
//	public Long getMembersCount(CcpJsonRepresentation parameters) {
//		CcpHttpRequester ccpHttp = CcpDependencyInjection.getDependency(CcpHttpRequester.class);
//
//		Long chatId = parameters.getAsLongNumber(JsonFieldNames.chatId);
//		String url = this.getCompleteUrl(parameters);
//		ccpHttp.executeHttpRequest(url + "/getChatMemberCount?chat_id=" + chatId, CcpHttpMethods.GET, CcpOtherConstants.EMPTY_JSON, "", 200);
//		CcpHttpHandler ccpHttpHandler = new CcpHttpHandler(200, url);
//
//		CcpJsonRepresentation response = ccpHttpHandler.executeHttpSimplifiedGet("getMembersCount", CcpHttpResponseType.singleRecord);
//		if(false == response.getAsBoolean(JsonFieldNames.ok)) {
//			throw new CcpErrorInstantMessengerChatErrorCount(chatId);
//		}
//		Long result = response.getAsLongNumber(JsonFieldNames.result);
//		return result;
//	}

	CcpInstantMessenger throwThisBotWasBlockedByThisUser(String token) {
		throw new CcpErrorInstantMessageThisBotWasBlockedByThisUser(token);
	}
	
	CcpInstantMessenger throwTooManyRequests() {
		throw new CcpHttpTooManyRequests();
	}
	
	public CcpJsonRepresentation sendTextMessage(CcpJsonFieldName botType, String botToken, Long chatId, Long replyTo, String message) {

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
		
		CcpHttpHandler httpHandler = this.getHttpHandler(botType, botToken, "/sendMessage");
		
		for (String text : texts) {
			CcpJsonRepresentation body = CcpOtherConstants.EMPTY_JSON
					.put(JsonFieldNames.reply_to_message_id, replyTo)
					.put(JsonFieldNames.parse_mode, "html")
					.put(JsonFieldNames.chat_id, "" + chatId)
					.put(JsonFieldNames.text, text);
			
			CcpJsonRepresentation response = httpHandler.executeHttpRequest("sendInstantMessage", CcpHttpMethods.POST, CcpOtherConstants.EMPTY_JSON, body, CcpHttpResponseType.singleRecord);
			
			CcpJsonRepresentation result = response.getInnerJson(JsonFieldNames.result);
			CcpStringDecorator sd = result.getAsStringDecorator(JsonFieldNames.message_id);
			boolean longNumber = sd.isLongNumber();
			if(longNumber) {
				replyTo = result.getAsLongNumber(JsonFieldNames.message_id);
			}
		}
		
		return CcpOtherConstants.EMPTY_JSON
				.put(JsonFieldNames.replyTo, replyTo)
				.put(JsonFieldNames.message, message)
				;
	}

	public CcpJsonRepresentation sendFile(CcpJsonFieldName botType, String botToken, Long chatId, Long replyTo, String fileName, String caption, Byte[] fileContent) {

		
		CcpHttpHandler httpHandler = this.getHttpHandler(botType, botToken, "/sendDocument");

		CcpHttpBodyBinary binary = new CcpHttpBodyBinary(CcpHttpContentType.TEXT_HTML, "document", fileName, fileContent);
		List<CcpHttpBodyBinary> binaries = Arrays.asList(binary);
		CcpHttpBodyText text = new CcpHttpBodyText(CcpHttpContentType.TEXT_PLAIN, "chat_id", "" + chatId);
		CcpHttpBodyText _caption = new CcpHttpBodyText(CcpHttpContentType.TEXT_PLAIN, "caption", caption);
		List<CcpHttpBodyText> texts = Arrays.asList(text, _caption);
		
		CcpHttpMethods method = CcpHttpMethods.POST;
		CcpJsonRepresentation result = httpHandler.executeMultiPartHttpRequest("", method, CcpOtherConstants.EMPTY_JSON, texts, binaries, CcpHttpResponseType.singleRecord);
		
		Double messageId = result.getValueFromPath(0d, JsonFieldNames.result, JsonFieldNames.message_id);
		
		CcpJsonRepresentation put = CcpOtherConstants.EMPTY_JSON
				.put(JsonFieldNames.fileName, fileName)
				.put(JsonFieldNames.caption, caption)
				.put(JsonFieldNames.message, new CcpStringDecorator(fileContent).content)
				.put(JsonFieldNames.replyTo, messageId)
				
				;
		
		return put;
	}

	private CcpHttpHandler getHttpHandler(CcpJsonFieldName botType, String botToken, String resource) {
		CcpJsonRepresentation properties = new CcpStringDecorator("application_properties").propertiesFrom().environmentVariablesOrClassLoaderOrFile();
		String botUrl = properties.getAsString(JsonFieldNames.urlInstantMessengerKey);
		String url = botUrl + botToken + resource;

		CcpJsonRepresentation handlers = CcpOtherConstants.EMPTY_JSON
				.addJsonTransformer(403, new CcpFunctionThrowException(new CcpErrorInstantMessageThisBotWasBlockedByThisUser(botType.name())))
				.addJsonTransformer(404, new CcpFunctionThrowException(new RuntimeException("The bot '" + botToken + "' was not found")))
				.addJsonTransformer(401, new CcpFunctionThrowException(new RuntimeException("The bot '" + botToken + "' is inactive")))
				.addJsonTransformer(429, new CcpFunctionThrowException(new CcpHttpTooManyRequests()))
				.addJsonTransformer(200, CcpOtherConstants.DO_NOTHING)
				;

		CcpHttpHandler httpHandler = new CcpHttpHandler(handlers, url);
		return httpHandler;
	}

}
