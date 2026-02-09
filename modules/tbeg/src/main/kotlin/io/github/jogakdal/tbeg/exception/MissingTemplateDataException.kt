package io.github.jogakdal.tbeg.exception

import io.github.jogakdal.tbeg.engine.rendering.RequiredNames

/**
 * 템플릿에 정의된 데이터가 DataProvider에 없을 때 발생하는 예외.
 *
 * [io.github.jogakdal.tbeg.MissingDataBehavior.THROW] 설정 시 발생한다.
 *
 * @property missingVariables 누락된 변수 이름 목록
 * @property missingCollections 누락된 컬렉션 이름 목록
 * @property missingImages 누락된 이미지 이름 목록
 */
class MissingTemplateDataException(
    val missingVariables: Set<String>,
    val missingCollections: Set<String>,
    val missingImages: Set<String>,
    message: String = buildMessage(missingVariables, missingCollections, missingImages)
) : RuntimeException(message) {

    /**
     * 누락된 데이터가 있는지 확인한다.
     */
    val hasMissingData: Boolean
        get() = missingVariables.isNotEmpty() || missingCollections.isNotEmpty() || missingImages.isNotEmpty()

    companion object {
        private fun buildMessage(
            variables: Set<String>,
            collections: Set<String>,
            images: Set<String>
        ): String = buildString {
            append("템플릿에 필요한 데이터가 누락되었습니다.")
            if (variables.isNotEmpty()) {
                append("\n  - 변수: ${variables.joinToString(", ")}")
            }
            if (collections.isNotEmpty()) {
                append("\n  - 컬렉션: ${collections.joinToString(", ")}")
            }
            if (images.isNotEmpty()) {
                append("\n  - 이미지: ${images.joinToString(", ")}")
            }
        }

        /**
         * RequiredNames와 실제 제공된 데이터를 비교하여 누락된 데이터가 있으면 예외를 생성한다.
         *
         * @return 누락된 데이터가 없으면 null, 있으면 예외 인스턴스
         */
        internal fun createIfMissing(
            required: RequiredNames,
            providedVariables: Set<String>,
            providedCollections: Set<String>,
            providedImages: Set<String>
        ): MissingTemplateDataException? {
            val missingVars = required.variables - providedVariables
            val missingColls = required.collections - providedCollections
            val missingImgs = required.images - providedImages

            return if (missingVars.isNotEmpty() || missingColls.isNotEmpty() || missingImgs.isNotEmpty()) {
                MissingTemplateDataException(missingVars, missingColls, missingImgs)
            } else {
                null
            }
        }
    }
}