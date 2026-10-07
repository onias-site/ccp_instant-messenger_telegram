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
import com.ccp.decorators.CcpPropertiesDecorator;

/**
 * {@code CcpInstantMessenger} implementation for Telegram. Sends text messages
 * (automatically split every 4096 characters) and files via multipart.
 * Handles HTTP errors 403 (bot blocked) and 429 (too many requests) by throwing the
 * matching exceptions.
 */
class TelegramInstantMessenger implements CcpInstantMessenger {
	/** Fields of the Telegram requests and of the results. */
	enum JsonFieldNames implements CcpJsonFieldName{
		/** Unused. */
		chatId,
		/** Unused. */
		recipient,
		/** The text sent. */
		message,
		/** Unused. */
		method,
		/** Id of the message being answered. */
		reply_to_message_id,
		/** Id of the target chat. */
		chat_id,
		/** Unused. */
		url,
		/** Property holding the base URL of the bot API. */
		urlInstantMessengerKey,
		/** Name of the file sent. */
		fileName,
		/** Caption of the file sent. */
		caption
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

	/**
	 * Throws the error of a bot blocked by the user.
	 * @param token the bot token
	 * @return never returns
	 */
	CcpInstantMessenger throwThisBotWasBlockedByThisUser(String token) {
		CcpErrorInstantMessageThisBotWasBlockedByThisUser ccpErrorInstantMessageThisBotWasBlockedByThisUser = new CcpErrorInstantMessageThisBotWasBlockedByThisUser(token);
		throw ccpErrorInstantMessageThisBotWasBlockedByThisUser;
	}
	
	/**
	 * Throws the error of the rate limit.
	 * @return never returns
	 */
	CcpInstantMessenger throwTooManyRequests() {
		CcpHttpTooManyRequests ccpHttpTooManyRequests = new CcpHttpTooManyRequests();
		throw ccpHttpTooManyRequests;
	}
	
	/**
	 * Sends the text as plain text, split in pieces of 4096 characters; each piece answers the previous one. A blank text
	 * sends nothing. Errors: 403 bot blocked, 404 bot not found, 401 bot inactive, 429 rate limit.
	 * @param botType the bot type, named in the blocked-bot error
	 * @param botToken the bot token
	 * @param chatId the target chat
	 * @param replyTo the message being answered by the first piece
	 * @param message the text
	 * @return {@code replyTo} (the id of the last piece sent) and {@code message}; an empty JSON for a blank text
	 */
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

	/**
	 * Sends the file as a document with a caption, as an answer to {@code replyTo} when it is greater than zero.
	 * @param botType the bot type, named in the blocked-bot error
	 * @param botToken the bot token
	 * @param chatId the target chat
	 * @param replyTo the message being answered; zero sends the file without answering any message
	 * @param fileName the file name
	 * @param caption the caption
	 * @param fileContent the file content
	 * @return {@code fileName}, {@code caption}, {@code message} (the content as text) and, when Telegram returned it,
	 * {@code replyTo} with the id of the sent message
	 */
	public CcpJsonRepresentation sendFile(CcpJsonFieldName botType, String botToken, Long chatId, Long replyTo, String fileName, String caption, Byte[] fileContent) {

		
		CcpHttpHandler httpHandler = this.getHttpHandler(botType, botToken, "/sendDocument");

		CcpHttpBodyBinary binary = new CcpHttpBodyBinary(CcpHttpContentType.TEXT_HTML, "document", fileName, fileContent);
		List<CcpHttpBodyBinary> binaries = Arrays.asList(binary);
		String chatIdAsText = "" + chatId;
		CcpHttpBodyText text = new CcpHttpBodyText(CcpHttpContentType.TEXT_PLAIN, "chat_id", chatIdAsText);
		CcpHttpBodyText captionBodyText = new CcpHttpBodyText(CcpHttpContentType.TEXT_PLAIN, "caption", caption);
		List<CcpHttpBodyText> texts = new ArrayList<>(Arrays.asList(text, captionBodyText));
		boolean isAReply = replyTo > 0;

		if(isAReply) {
			String replyToAsText = "" + replyTo;
			String replyToFieldName = JsonFieldNames.reply_to_message_id.name();
			CcpHttpBodyText replyToBodyText = new CcpHttpBodyText(CcpHttpContentType.TEXT_PLAIN, replyToFieldName, replyToAsText);
			texts.add(replyToBodyText);
		}

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

	/**
	 * Builds the HTTP handler of a bot API method, with the URL read from {@code application_properties} and the error
	 * statuses mapped to their exceptions.
	 * @param botType the bot type
	 * @param botToken the bot token
	 * @param resource the API method path (e.g. "/sendMessage")
	 * @return the handler
	 */
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
