package io.github.jogakdal.tbeg.internal

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.full.companionObject

internal fun <R : Any> R.commonLogger(): Lazy<Logger> =
    lazy { LoggerFactory.getLogger(unwrapCompanionClass(this.javaClass).name) }

private fun <T : Any> unwrapCompanionClass(ofClass: Class<T>): Class<*> =
    ofClass.enclosingClass?.takeIf {
        ofClass.enclosingClass.kotlin.companionObject?.java == ofClass
    } ?: ofClass
