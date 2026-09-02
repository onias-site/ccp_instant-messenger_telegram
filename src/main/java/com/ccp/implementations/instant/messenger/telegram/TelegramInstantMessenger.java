package com.ccp.implementations.instant.messenger.telegram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ccp.constants.CcpOtherConstants;
import com.ccp.decorators.CcpJsonRepresentation;
import com.ccp.decorators.CcpJsonFieldName;
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
import com.ccp.decorators.CcpPropertiesDecorator;/**
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
		CcpErrorInstantMessageThisBotWasBlockedByThisUser ccpErrorInstantMessageThisBotWasBlockedByThisUser = new CcpErrorInstantMessageThisBotWasBlockedByThisUser(token);
		throw ccpErrorInstantMessageThisBotWasBlockedByThisUser;
	}
	
	CcpInstantMessenger throwTooManyRequests() {
		CcpHttpTooManyRequests ccpHttpTooManyRequests = new CcpHttpTooManyRequests();
		throw ccpHttpTooManyRequests;
	}
	
	public CcpJsonRepresentation sendTextMessage(CcpJsonFieldName botType, String botToken, Long chatId, Long replyTo, String message) {
		String messageTrim = message.trim();
		boolean messageTrimEmpty = messageTrim.isEmpty();

		if(messageTrimEmpty) {
			return CcpOtherConstants.EMPTY_JSON;
		}

		List<String> texts = new ArrayList<>();
		int length = message.length();
		int pieces = length / 4096;
		
		for(int k = 0; k <= pieces; k++) {
			int kMais = k + 1;
			int nextBound = (kMais) * 4096;
			int currentBound = k * 4096;
			boolean nextBoundMaior = nextBound > length;
			String text = message.substring(currentBound, nextBoundMaior ? length : nextBound);
			texts.add(text);
		}
		
		CcpHttpHandler httpHandler = this.getHttpHandler(botType, botToken, "/sendMessage");
		
		for (String text : texts) {
			CcpJsonRepresentation put2 = CcpOtherConstants.EMPTY_JSON
					.put(JsonFieldNames.reply_to_message_id, replyTo);
					CcpJsonRepresentation put3 = put2
					.put(JsonFieldNames.parse_mode, "html");
					String valorMais = "" + chatId;
					CcpJsonRepresentation put4 = put3
					.put(JsonFieldNames.chat_id, valorMais);
					CcpJsonRepresentation body = put4
					.put(JsonFieldNames.text, text);
			
			CcpJsonRepresentation response = httpHandler.executeHttpRequest("sendInstantMessage", CcpHttpMethods.POST, CcpOtherConstants.EMPTY_JSON, body, CcpHttpResponseType.singleRecord);
			
			CcpJsonRepresentation result = response.getInnerJson(JsonFieldNames.result);
			CcpStringDecorator sd = result.getAsStringDecorator(JsonFieldNames.message_id);
			boolean longNumber = sd.isLongNumber();
			if(longNumber) {
				replyTo = result.getAsLongNumber(JsonFieldNames.message_id);
			}
		}
		CcpJsonRepresentation put5 = CcpOtherConstants.EMPTY_JSON
				.put(JsonFieldNames.replyTo, replyTo);
				CcpJsonRepresentation put6 = put5
				.put(JsonFieldNames.message, message);

				return put6
				;
	}

	public CcpJsonRepresentation sendFile(CcpJsonFieldName botType, String botToken, Long chatId, Long replyTo, String fileName, String caption, Byte[] fileContent) {

		
		CcpHttpHandler httpHandler = this.getHttpHandler(botType, botToken, "/sendDocument");

		CcpHttpBodyBinary binary = new CcpHttpBodyBinary(CcpHttpContentType.TEXT_HTML, "document", fileName, fileContent);
		List<CcpHttpBodyBinary> binaries = Arrays.asList(binary);
		String valorMais2 = "" + chatId;
		CcpHttpBodyText text = new CcpHttpBodyText(CcpHttpContentType.TEXT_PLAIN, "chat_id", valorMais2);
		CcpHttpBodyText _caption = new CcpHttpBodyText(CcpHttpContentType.TEXT_PLAIN, "caption", caption);
		List<CcpHttpBodyText> texts = Arrays.asList(text, _caption);
		
		CcpHttpMethods method = CcpHttpMethods.POST;
		CcpJsonRepresentation result = httpHandler.executeMultiPartHttpRequest("", method, CcpOtherConstants.EMPTY_JSON, texts, binaries, CcpHttpResponseType.singleRecord);
		
		Double messageId = result.getValueFromPath(0d, JsonFieldNames.result, JsonFieldNames.message_id);
		CcpJsonRepresentation put7 = CcpOtherConstants.EMPTY_JSON
				.put(JsonFieldNames.fileName, fileName);
				CcpJsonRepresentation put8 = put7
				.put(JsonFieldNames.caption, caption);
				CcpStringDecorator ccpStringDecorator = new CcpStringDecorator(fileContent);
				CcpJsonRepresentation put9 = put8
				.put(JsonFieldNames.message, ccpStringDecorator.content);

				CcpJsonRepresentation put = put9
				.put(JsonFieldNames.replyTo, messageId)
				
				;
		
		return put;
	}

	private CcpHttpHandler getHttpHandler(CcpJsonFieldName botType, String botToken, String resource) {
		CcpStringDecorator ccpStringDecorator2 = new CcpStringDecorator("application_properties");
		CcpPropertiesDecorator propertiesFrom = ccpStringDecorator2.propertiesFrom();
		CcpJsonRepresentation properties = propertiesFrom.environmentVariablesOrClassLoaderOrFile();
		String botUrl = properties.getAsString(JsonFieldNames.urlInstantMessengerKey);
		String botUrlMais = botUrl + botToken;
		String url = botUrlMais + resource;
		String botTypeName = botType.name();
		CcpErrorInstantMessageThisBotWasBlockedByThisUser ccpErrorInstantMessageThisBotWasBlockedByThisUser2 = new CcpErrorInstantMessageThisBotWasBlockedByThisUser(botTypeName);
		CcpFunctionThrowException ccpFunctionThrowException = new CcpFunctionThrowException(ccpErrorInstantMessageThisBotWasBlockedByThisUser2);
		CcpJsonRepresentation addJsonTransformer = CcpOtherConstants.EMPTY_JSON
				.addJsonTransformer(403, ccpFunctionThrowException);
				CcpErrorTelegramBotNotFound ccpErrorTelegramBotNotFound = new CcpErrorTelegramBotNotFound(botToken);
				CcpFunctionThrowException ccpFunctionThrowException2 = new CcpFunctionThrowException(ccpErrorTelegramBotNotFound);
				CcpJsonRepresentation addJsonTransformer2 = addJsonTransformer
				.addJsonTransformer(404, ccpFunctionThrowException2);
				CcpErrorTelegramBotIsInactive ccpErrorTelegramBotIsInactive = new CcpErrorTelegramBotIsInactive(botToken);
				CcpFunctionThrowException ccpFunctionThrowException3 = new CcpFunctionThrowException(ccpErrorTelegramBotIsInactive);
				CcpJsonRepresentation addJsonTransformer3 = addJsonTransformer2
				.addJsonTransformer(401, ccpFunctionThrowException3);
				CcpHttpTooManyRequests ccpHttpTooManyRequests2 = new CcpHttpTooManyRequests();
				CcpFunctionThrowException ccpFunctionThrowException4 = new CcpFunctionThrowException(ccpHttpTooManyRequests2);
				CcpJsonRepresentation addJsonTransformer4 = addJsonTransformer3
				.addJsonTransformer(429, ccpFunctionThrowException4);

				CcpJsonRepresentation handlers = addJsonTransformer4
				.addJsonTransformer(200, CcpOtherConstants.DO_NOTHING)
				;

		CcpHttpHandler httpHandler = new CcpHttpHandler(handlers, url);
		return httpHandler;
	}

	/**
	 * Exceção lançada quando o Telegram responde 404 para o token informado, ou seja, o bot não existe.
	 */
	@SuppressWarnings("serial")
	public static class CcpErrorTelegramBotNotFound extends RuntimeException {
		/**
		 * Monta a mensagem informando qual token de bot não foi encontrado.
		 * @param botToken o token do bot procurado
		 */
		private CcpErrorTelegramBotNotFound(String botToken) {
			super("The bot '" + botToken + "' was not found");
		}
	}

	/**
	 * Exceção lançada quando o Telegram responde 401 para o token informado, ou seja, o bot existe mas está inativo.
	 */
	@SuppressWarnings("serial")
	public static class CcpErrorTelegramBotIsInactive extends RuntimeException {
		/**
		 * Monta a mensagem informando qual token de bot está inativo.
		 * @param botToken o token do bot inativo
		 */
		private CcpErrorTelegramBotIsInactive(String botToken) {
			super("The bot '" + botToken + "' is inactive");
		}
	}

}
