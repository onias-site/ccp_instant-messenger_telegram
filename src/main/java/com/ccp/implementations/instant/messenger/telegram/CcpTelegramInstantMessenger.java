package com.ccp.implementations.instant.messenger.telegram;

import com.ccp.dependency.injection.CcpInstanceProvider;
import com.ccp.especifications.instant.messenger.CcpInstantMessenger;;

/**
 * DI provider that exposes {@code TelegramInstantMessenger} as the {@code CcpInstantMessenger} implementation.
 */
public class CcpTelegramInstantMessenger implements CcpInstanceProvider<CcpInstantMessenger> {

	/**
	 * Builds the Telegram implementation of {@code CcpInstantMessenger}.
	 * @return a new {@code TelegramInstantMessenger}
	 */
	public CcpInstantMessenger getInstance() {
		TelegramInstantMessenger telegramInstantMessenger = new TelegramInstantMessenger();
		return telegramInstantMessenger;
	}
 
}
