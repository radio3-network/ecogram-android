package offgrid.geogram.ble.events;

import offgrid.geogram.core.Log;
import offgrid.geogram.events.EventAction;

public class EventBleMessageReceived extends EventAction {
    private static final String TAG = "EventBleMessageReceived";

    public EventBleMessageReceived(String id) {
        super(id);
    }

    @Override
    public void action(Object... data) {
        String message = (String) data[0];
        Log.i(TAG, "-->> Received message: " + message);
        // Handle the received message here

        // add the message on the broadcast window
    }
}
