package com.js.salesman.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ProductListResponse {

    @SerializedName(value = "success", alternate = {"is_success"})
    private boolean success;

    @SerializedName(value = "status", alternate = {"state"})
    private String status;

    @SerializedName(value = "code", alternate = {"status_code"})
    private int code;

    private String message;
    private int count;

    @SerializedName(value = "data", alternate = {"products", "items", "data_list"})
    private List<Product> data;

    @SerializedName(value = "timestamp", alternate = {"lastSync", "last_sync", "sync_timestamp", "server_time"})
    private String timestamp;

    public boolean isSuccess() {
        return success || "success".equalsIgnoreCase(status) || code == 200 || (data != null && !data.isEmpty());
    }

    public String getStatus() { return status; }
    public int getCode() { return code; }
    public String getMessage() { return message; }
    public int getCount() { return count; }
    public List<Product> getData() { return data; }
    public String getTimestamp() { return timestamp; }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setData(List<Product> data) {
        this.data = data;
    }
}
