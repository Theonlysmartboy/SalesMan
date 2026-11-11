package com.js.salesman.models.responses;

/**
 * Standard backend envelope: { "code": 200, "success": true, "message": "...", "data": ... }.
 *
 * @param <T> type of the {@code data} payload
 */
public class DeviceApiResponse<T> {
    public int code;
    public boolean success;
    public String message;
    public T data;
}
