package com.js.salesman.utils;

import android.os.Handler;

import com.js.salesman.session.SessionManager;

public class IdleManager {

    private static final long IDLE_TIMEOUT = 3 * 60 * 1000; // 5 min

    private final SessionManager session;
    private final Runnable onIdle;
    private final Handler handler = new Handler();

    private final Runnable checker = new Runnable() {
        @Override
        public void run() {
            long last = session.getLastActivity();
            long now = System.currentTimeMillis();

            if (now - last > IDLE_TIMEOUT) {
                onIdle.run();
            } else {
                handler.postDelayed(this, 1000);
            }
        }
    };

    public IdleManager(SessionManager session, Runnable onIdle) {
        this.session = session;
        this.onIdle = onIdle;
    }

    public void start() {
        handler.post(checker);
    }

    public void stop() {
        handler.removeCallbacks(checker);
    }

    public void userInteracted() {
        session.updateLastActivity();
    }
}
