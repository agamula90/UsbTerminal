package com.ismet.usbterminal.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AccessoryOperationDispatcher(val operationType: String = "read")
