package com.js.salesman.models;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.js.salesman.data.repo.AuthRepository;
import com.js.salesman.session.SessionManager;

import org.json.JSONObject;

    public class LoginViewModel extends ViewModel {
        private final MutableLiveData<Boolean> loading = new MutableLiveData<>();
        private final MutableLiveData<String> error = new MutableLiveData<>();
        private final MutableLiveData<Boolean> loginSuccess = new MutableLiveData<>();
        private final AuthRepository repository = new AuthRepository();
        public LiveData<Boolean> getLoading() { return loading; }
        public LiveData<String> getError() { return error; }
        public LiveData<Boolean> getLoginSuccess() { return loginSuccess; }
        public void login(Context context, String baseUrl, String username, String password) {
            loading.postValue(true);
            repository.login(baseUrl, username, password, new AuthRepository.LoginCallback() {
                        @Override
                        public void onSuccess(String token, JSONObject user) {
                            try {
                                SessionManager session = new SessionManager(context);
                                session.saveSession(token, user.getInt("id"),
                                        user.getString("username"),
                                        user.getString("role"),
                                        user.getString("full_name"));
                                loading.postValue(false);
                                loginSuccess.postValue(true);
                            } catch (Exception e) {
                                error.postValue("Session save failed");
                            }
                        }
                        @Override
                        public void onError(String message) {
                            loading.postValue(false);
                            error.postValue(message);
                        }
                    });
        }
    }
