package com.telegramreader.app.telegram

import org.drinkless.tdlib.TdApi

class TdException(val error: TdApi.Error) : Exception("TDLib error ${error.code}: ${error.message}")
