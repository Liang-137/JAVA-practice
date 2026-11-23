package cn.edu.campus.food.support;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public static BusinessException missing(String resource) {
        return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + "不存在或不属于当前用户");
    }
    public static BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message);
    }
}
