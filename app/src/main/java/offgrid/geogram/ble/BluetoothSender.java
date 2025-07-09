package offgrid.geogram.ble;

import static offgrid.geogram.ble.BluetoothCentral.advertiseDurationMillis;
import static offgrid.geogram.ble.BluetoothCentral.selfIntervalSeconds;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;

import androidx.annotation.RequiresApi;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import offgrid.geogram.core.Log;

@SuppressLint("ObsoleteSdkInt")
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class BluetoothSender {

    // Self-advertising
    private String selfMessage = ">CR7BBQ " + System.currentTimeMillis();
    private static final String TAG = "BluetoothSender";
    private static final UUID SERVICE_UUID = UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB");
    private static BluetoothSender instance;
    private final Context context;
    private final Handler handler = new Handler();
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private boolean isRunning = false;
    private boolean isPaused = false;

    private final Queue<String> messageQueue = new LinkedList<>();
    private boolean isSending = false;

    private final Runnable selfAdvertiseTask = new Runnable() {
        @Override
        public void run() {
            if (isRunning && !isPaused && selfMessage != null) {
                selfMessage = ">CR7BBQ " + System.currentTimeMillis();
                sendMessage(selfMessage);
                handler.postDelayed(this, selfIntervalSeconds * 1000L);
            }
        }
    };

    private BluetoothSender(Context context) {
        this.context = context.getApplicationContext();
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager != null && manager.getAdapter() != null) {
            advertiser = manager.getAdapter().getBluetoothLeAdvertiser();
        }
    }

    public static synchronized BluetoothSender getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothSender(context);
        }
        return instance;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        isPaused = false;
        Log.i(TAG, "Bluetooth sender started.");
        tryToSendNext();

        // Start self-advertise loop
        if (selfMessage != null) {
            handler.post(selfAdvertiseTask);
        }
    }

    public void stop() {
        isRunning = false;
        isPaused = false;
        stopAdvertising();
        messageQueue.clear();
        handler.removeCallbacks(selfAdvertiseTask);
        Log.i(TAG, "BluetoothSender stopped and queue cleared.");
    }

    public void pause() {
        if (!isPaused) {
            isPaused = true;
            stopAdvertising();
            handler.removeCallbacks(selfAdvertiseTask);
            Log.i(TAG, "BluetoothSender paused.");
        }
    }

    public void resume() {
        if (isPaused) {
            isPaused = false;
            Log.i(TAG, "BluetoothSender resumed.");
            tryToSendNext();
            if (selfMessage != null) {
                handler.post(selfAdvertiseTask);
            }
        }
    }

    public void sendMessage(String message) {
        if (message == null || message.isEmpty()) return;

        messageQueue.offer(message);
        Log.i(TAG, "Queued message: " + message);

        if (isRunning && !isPaused) {
            tryToSendNext();
        }
    }

    private void tryToSendNext() {
        if (!isRunning || isPaused || isSending || messageQueue.isEmpty()) return;

        String message = messageQueue.peek();
        //String message = messageQueue.poll();
        if (message == null) return;

        if (!hasAdvertisePermission()) {
            Log.i(TAG, "Missing BLUETOOTH_ADVERTISE permission. Cannot send BLE message.");
            return;
        }

        AdvertiseData data = buildAdvertiseData(message);
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build();

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {

                handler.postDelayed(() -> {
                    Log.i(TAG, "Advertising message: " + message);
                    isSending = true;
                    // don't listen while sending data
                    BluetoothListener.getInstance(context).pauseListening();
                    stopAdvertising();
                    // take the next message out of the poll
                    String removed = messageQueue.poll();
                    Log.i(TAG, "Message sent and removed from queue: " + removed);
                    isSending = false;
                    tryToSendNext();
                    // unblock the sending
                    BluetoothListener.getInstance(context).resumeListening();
                }, advertiseDurationMillis);
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.i(TAG, "Failed to advertise message. Error code: " + errorCode);
                isSending = false;
            }
        };

        try {
            advertiser.startAdvertising(settings, data, advertiseCallback);
        } catch (SecurityException e) {
            Log.i(TAG, "SecurityException while advertising: " + e.getMessage());
        }
    }


    public void setSelfMessage(String message) {
        this.selfMessage = message;
        Log.i(TAG, "Self message set to: " + message);
        if (isRunning && !isPaused) {
            handler.removeCallbacks(selfAdvertiseTask);
            handler.post(selfAdvertiseTask);
        }
    }

    public void setSelfIntervalSeconds(int seconds) {
        if (seconds <= 0) return;
        selfIntervalSeconds = seconds;
        Log.i(TAG, "Self-advertise interval set to: " + seconds + " seconds");

        if (isRunning && !isPaused && selfMessage != null) {
            handler.removeCallbacks(selfAdvertiseTask);
            handler.post(selfAdvertiseTask);
        }
    }


    private void stopAdvertising() {
        if (advertiser != null && advertiseCallback != null) {
            if (!hasAdvertisePermission()) {
                Log.i(TAG, "Missing BLUETOOTH_ADVERTISE permission. Cannot stop BLE advertiser.");
                return;
            }
            try {
                advertiser.stopAdvertising(advertiseCallback);
            } catch (SecurityException e) {
                Log.i(TAG, "SecurityException while stopping advertiser: " + e.getMessage());
            }
        }
        advertiseCallback = null;
        isSending = false;
    }

    private AdvertiseData buildAdvertiseData(String message) {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 24) {
            Log.i(TAG, "Message too long, truncating to 24 bytes.");
            byte[] truncated = new byte[24];
            System.arraycopy(bytes, 0, truncated, 0, 24);
            bytes = truncated;
        }

        return new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .addServiceData(new ParcelUuid(SERVICE_UUID), bytes)
                .setIncludeDeviceName(false)
                .build();
    }

    private boolean hasAdvertisePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
}
