package com.ccp.implementations.instant.messenger.telegram;

import com.ccp.dependency.injection.CcpInstanceProvider;
import com.ccp.especifications.instant.messenger.CcpInstantMessenger;;

/**
 * Provedor de DI que expõe {@code TelegramInstantMessenger} como implementação de {@code CcpInstantMessenger}.
 */
public class CcpTelegramInstantMessenger implements CcpInstanceProvider<CcpInstantMessenger> {

	public CcpInstantMessenger getInstance() {
		return new TelegramInstantMessenger();
	}

}
