package offgrid.geogram.ble.events;

import offgrid.geogram.ble.BluetoothMessage;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;
import offgrid.geogram.events.EventAction;
import offgrid.geogram.old.bluetooth_old.broadcast.BroadcastMessage;

public class EventBleBroadcastMessageSent extends EventAction {
    private static final String TAG = "EventBleMessageSent";

    public EventBleBroadcastMessageSent(String id) {
        super(id);
    }

    @Override
    public void action(Object... data) {
        BluetoothMessage message = (BluetoothMessage) data[0];
        //Log.i(TAG, "-->> Sent message: " + message.getOutput());
        BroadcastMessage broadCastMessage = new BroadcastMessage(
                message.getMessage(),
                "Me",
                true
                );
        // add the message on the broadcast window
        Central.getInstance().broadcastChatFragment.addUserMessage(broadCastMessage);
    }
}
