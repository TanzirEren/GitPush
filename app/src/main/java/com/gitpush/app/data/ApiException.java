package com.gitpush.app.data;

public class ApiException extends Exception {
    public final int code;
    public ApiException(int code, String message) { super(message); this.code = code; }
}
