package com.example.demo.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

interface ResponseDto {}

data class ErrorResponseDto(
    val errorCode: String,
    val message: String?
) : ResponseDto

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(value = [CustomException::class])
    fun handlingCustomException(ex: CustomException): ResponseEntity<ErrorResponseDto> {
        val errorCode: ErrorCode = ex.errorCode
        val errorDto = ErrorResponseDto(errorCode = errorCode.name, message = errorCode.message)
        return ResponseEntity(errorDto, errorCode.status)
    }
}

class CustomException(
    val errorCode: ErrorCode
) : RuntimeException()

enum class ErrorCode(val status: HttpStatus, val message: String) {
    // 400 - Bad Request
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "입력 파라미터가 올바르지 않습니다."),
    INVALID_BLOCK_TYPE(HttpStatus.BAD_REQUEST, "블록 타입이 올바르지 않습니다."),
    INVALID_VM_FEATURES(HttpStatus.BAD_REQUEST, "VM블록 Features값이 올바르지 않습니다."),
    INVALID_WEBSERVER_FEATURES(HttpStatus.BAD_REQUEST, "Web Server블록 Features값이 올바르지 않습니다."),
    INVALID_DB_FEATURES(HttpStatus.BAD_REQUEST, "DB블록 Features값이 올바르지 않습니다."),
    INVALID_WEBSERVER_NAME(HttpStatus.BAD_REQUEST, "Web Server블록 name값이 올바르지 않습니다."),
    INVALID_DB_NAME(HttpStatus.BAD_REQUEST, "DB블록 name값이 올바르지 않습니다."),
    INVALID_DB_USERNAME(HttpStatus.BAD_REQUEST, "DB블록 username값이 올바르지 않습니다."),
    INVALID_DB_USER_PASSWORD(HttpStatus.BAD_REQUEST, "DB블록 user password값이 올바르지 않습니다."),

    // 401 - Unauthorized

    // 403 - Forbidden

    // 404 - Not Found

    // 409 - Conflict

    // 500 - Internal Server Error
    SKETCH_DEPLOYMENT_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "스케치 배포 요청에 실패했습니다.")
}
