package com.ccp.implementations.instant.messenger.telegram;

import com.ccp.dependency.injection.CcpInstanceProvider;
import com.ccp.especifications.instant.messenger.CcpInstantMessenger;;

public class CcpTelegramInstantMessenger implements CcpInstanceProvider<CcpInstantMessenger> {

	public CcpInstantMessenger getInstance() {
		return new TelegramInstantMessenger();
	}

}
