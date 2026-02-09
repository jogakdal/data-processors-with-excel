package io.github.jogakdal.tbeg.internal

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.full.companionObject

/**
 * property delegate를 이용한 공통 로깅 함수
 * 어떤 클래스에서든 다음과 같이 사용하면 된다.
 *
 * class AnyClass {
 *    companion object {
 *        val LOG by commonLogger()
 *    }
 *
 *    fun someFunc() {
 *        LOG.info("Log Message")
 *    }
 * }
 */
internal fun <R : Any> R.commonLogger(): Lazy<Logger> =
    lazy { LoggerFactory.getLogger(unwrapCompanionClass(this.javaClass).name) }

private fun <T : Any> unwrapCompanionClass(ofClass: Class<T>): Class<*> =
    ofClass.enclosingClass?.takeIf {
        ofClass.enclosingClass.kotlin.companionObject?.java == ofClass
    } ?: ofClass
