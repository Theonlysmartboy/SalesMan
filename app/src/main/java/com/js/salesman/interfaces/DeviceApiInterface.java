package com.js.salesman.interfaces;

import com.js.salesman.models.DeviceApiResponse;
import com.js.salesman.models.DeviceChallengeData;
import com.js.salesman.models.DeviceChallengeRequest;
import com.js.salesman.models.DeviceRegisterData;
import com.js.salesman.models.DeviceRegisterRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Device-binding endpoints. Paths are relative to the same base URL used for {@code auth.php}.
 */
public interface DeviceApiInterface {

    /** {@code POST devices.php?action=register} */
    @POST("devices.php")
    Call<DeviceApiResponse<DeviceRegisterData>> register(
            @Query("action") String action,
            @Body DeviceRegisterRequest request);

    /** {@code POST devices.php?action=challenge} */
    @POST("devices.php")
    Call<DeviceApiResponse<DeviceChallengeData>> challenge(
            @Query("action") String action,
            @Body DeviceChallengeRequest request);
}
