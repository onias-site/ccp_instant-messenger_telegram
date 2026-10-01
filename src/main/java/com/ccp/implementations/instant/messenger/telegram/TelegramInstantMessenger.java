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
import com.ccp.json.fields.validation.CcpJsonCommonsFields;
import com.ccp.decorators.CcpPropertiesDecorator;/**
 * {@code CcpInstantMessenger} implementation for Telegram. Sends text messages
 * (automatically split every 4096 characters) and files via multipart.
 * Handles HTTP errors 403 (bot blocked) and 429 (too many requests) by throwing the
 * matching exceptions.
 */

class TelegramInstantMessenger implements CcpInstantMessenger {
	enum JsonFieldNames implements CcpJsonFieldName{
		chatId, recipient, message, method, reply_to_message_id, chat_id, url, urlInstantMessengerKey, fileName, caption
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
			int nextPieceIndex = k + 1;
			int nextBound = (nextPieceIndex) * 4096;
			int currentBound = k * 4096;
			boolean nextBoundExceedsLength = nextBound > length;
			String text = message.substring(currentBound, nextBoundExceedsLength ? length : nextBound);
			texts.add(text);
		}
		
		CcpHttpHandler httpHandler = this.getHttpHandler(botType, botToken, "/sendMessage");
		
		for (String text : texts) {
			// plain text, without parse_mode: no message sent by the system uses html formatting, and with
			// parse_mode html Telegram refused (400, can't parse entities) any text with '<' or '&', such as
			// the "aprovar <justificativa>" options of the support bot or a stack trace with "<init>"
			CcpJsonRepresentation bodyWithReplyTo = CcpOtherConstants.EMPTY_JSON
					.put(JsonFieldNames.reply_to_message_id, replyTo);
					String chatIdAsText = "" + chatId;
					CcpJsonRepresentation bodyWithChatId = bodyWithReplyTo
					.put(JsonFieldNames.chat_id, chatIdAsText);
					CcpJsonRepresentation body = bodyWithChatId
					.put(CcpJsonCommonsFields.text, text);
			
			CcpJsonRepresentation response = httpHandler.executeHttpRequest("sendInstantMessage", CcpHttpMethods.POST, CcpOtherConstants.EMPTY_JSON, body, CcpHttpResponseType.singleRecord);
			
			CcpJsonRepresentation result = response.getInnerJson(CcpJsonCommonsFields.result);
			CcpStringDecorator messageIdDecorator = result.getAsStringDecorator(CcpJsonCommonsFields.message_id);
			boolean messageIdIsNumber = messageIdDecorator.isLongNumber();
			if(messageIdIsNumber) {
				replyTo = result.getAsLongNumber(CcpJsonCommonsFields.message_id);
			}
		}
		CcpJsonRepresentation resultWithReplyTo = CcpOtherConstants.EMPTY_JSON
				.put(CcpJsonCommonsFields.replyTo, replyTo);
				CcpJsonRepresentation resultWithMessage = resultWithReplyTo
				.put(JsonFieldNames.message, message);

				return resultWithMessage
				;
	}

	public CcpJsonRepresentation sendFile(CcpJsonFieldName botType, String botToken, Long chatId, Long replyTo, String fileName, String caption, Byte[] fileContent) {

		
		CcpHttpHandler httpHandler = this.getHttpHandler(botType, botToken, "/sendDocument");

		CcpHttpBodyBinary binary = new CcpHttpBodyBinary(CcpHttpContentType.TEXT_HTML, "document", fileName, fileContent);
		List<CcpHttpBodyBinary> binaries = Arrays.asList(binary);
		String chatIdAsText = "" + chatId;
		CcpHttpBodyText text = new CcpHttpBodyText(CcpHttpContentType.TEXT_PLAIN, "chat_id", chatIdAsText);
		CcpHttpBodyText captionBodyText = new CcpHttpBodyText(CcpHttpContentType.TEXT_PLAIN, "caption", caption);
		List<CcpHttpBodyText> texts = Arrays.asList(text, captionBodyText);
		
		CcpHttpMethods method = CcpHttpMethods.POST;
		CcpJsonRepresentation result = httpHandler.executeMultiPartHttpRequest("", method, CcpOtherConstants.EMPTY_JSON, texts, binaries, CcpHttpResponseType.singleRecord);
		
		Double messageId = result.getValueFromPath(0d, CcpJsonCommonsFields.result, CcpJsonCommonsFields.message_id);
		CcpJsonRepresentation resultWithFileName = CcpOtherConstants.EMPTY_JSON
				.put(JsonFieldNames.fileName, fileName);
				CcpJsonRepresentation resultWithCaption = resultWithFileName
				.put(JsonFieldNames.caption, caption);
				CcpStringDecorator fileContentDecorator = new CcpStringDecorator(fileContent);
				CcpJsonRepresentation sentFileResult = resultWithCaption
				.put(JsonFieldNames.message, fileContentDecorator.content);
				boolean hasReplyTo = messageId > 0;
				
				if(hasReplyTo) {
					sentFileResult = sentFileResult.put(CcpJsonCommonsFields.replyTo, messageId);
					
				}
				
		
		return sentFileResult;
	}

	private CcpHttpHandler getHttpHandler(CcpJsonFieldName botType, String botToken, String resource) {
		CcpStringDecorator propertiesFileName = new CcpStringDecorator("application_properties");
		CcpPropertiesDecorator propertiesDecorator = propertiesFileName.propertiesFrom();
		CcpJsonRepresentation properties = propertiesDecorator.environmentVariablesOrClassLoaderOrFile();
		String botUrl = properties.getAsString(JsonFieldNames.urlInstantMessengerKey);
		String botUrlWithToken = botUrl + botToken;
		String url = botUrlWithToken + resource;
		String botTypeName = botType.name();
		CcpErrorInstantMessageThisBotWasBlockedByThisUser botBlockedError = new CcpErrorInstantMessageThisBotWasBlockedByThisUser(botTypeName);
		CcpFunctionThrowException throwBotBlocked = new CcpFunctionThrowException(botBlockedError);
		CcpJsonRepresentation errorHandlersWithBlocked = CcpOtherConstants.EMPTY_JSON
				.addJsonTransformer(403, throwBotBlocked);
				CcpErrorTelegramBotNotFound ccpErrorTelegramBotNotFound = new CcpErrorTelegramBotNotFound(botToken);
				CcpFunctionThrowException throwBotNotFound = new CcpFunctionThrowException(ccpErrorTelegramBotNotFound);
				CcpJsonRepresentation errorHandlersWithNotFound = errorHandlersWithBlocked
				.addJsonTransformer(404, throwBotNotFound);
				CcpErrorTelegramBotIsInactive ccpErrorTelegramBotIsInactive = new CcpErrorTelegramBotIsInactive(botToken);
				CcpFunctionThrowException throwBotInactive = new CcpFunctionThrowException(ccpErrorTelegramBotIsInactive);
				CcpJsonRepresentation errorHandlersWithInactive = errorHandlersWithNotFound
				.addJsonTransformer(401, throwBotInactive);
				CcpHttpTooManyRequests tooManyRequestsError = new CcpHttpTooManyRequests();
				CcpFunctionThrowException throwTooManyRequestsError = new CcpFunctionThrowException(tooManyRequestsError);
				CcpJsonRepresentation errorHandlersWithTooManyRequests = errorHandlersWithInactive
				.addJsonTransformer(429, throwTooManyRequestsError);

				CcpJsonRepresentation handlers = errorHandlersWithTooManyRequests
				.addJsonTransformer(200, CcpOtherConstants.DO_NOTHING)
				;

		CcpHttpHandler httpHandler = new CcpHttpHandler(handlers, url);
		return httpHandler;
	}

	/**
	 * Exception thrown when Telegram answers 404 for the given token, meaning the bot does not exist.
	 */
	@SuppressWarnings("serial")
	public static class CcpErrorTelegramBotNotFound extends RuntimeException {
		/**
		 * Builds the message stating which bot token was not found.
		 * @param botToken the token of the bot being looked up
		 */
		private CcpErrorTelegramBotNotFound(String botToken) {
			super("The bot '" + botToken + "' was not found");
		}
	}

	/**
	 * Exception thrown when Telegram answers 401 for the given token, meaning the bot exists but is inactive.
	 */
	@SuppressWarnings("serial")
	public static class CcpErrorTelegramBotIsInactive extends RuntimeException {
		/**
		 * Builds the message stating which bot token is inactive.
		 * @param botToken the token of the inactive bot
		 */
		private CcpErrorTelegramBotIsInactive(String botToken) {
			super("The bot '" + botToken + "' is inactive");
		}
	}

}
