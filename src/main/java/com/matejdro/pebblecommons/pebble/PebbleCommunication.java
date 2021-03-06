package com.matejdro.pebblecommons.pebble;

import android.content.SharedPreferences;
import android.os.Handler;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.matejdro.pebblecommons.PebbleCompanionApplication;

import java.util.Deque;
import java.util.LinkedList;

import timber.log.Timber;

public class PebbleCommunication
{
    private PebbleTalkerService talkerService;

    private Deque<CommModule> queuedModules;
    private int lastSentPacket;
    private boolean commBusy;

    private PebbleDictionary lastPacket;
    private int retryCount;

    private PebbleCapabilities connectedWatchCapabilities;
    private Handler retryHandler;

    public PebbleCommunication(PebbleTalkerService talkerService)
    {
        this.talkerService = talkerService;
        queuedModules = new LinkedList<CommModule>();
        commBusy = false;
        lastSentPacket = -1;
        retryCount = 0;
        retryHandler = new Handler(talkerService.getPebbleThreadHandler().getLooper());

        connectedWatchCapabilities = PebbleCapabilities.fromSerializedForm(talkerService.getGlobalSettings().getInt("LastWatchCapabilities", PebbleCapabilities.BASIC_CAPABILITIES_SERIALIZED));
    }

    public void sendToPebble(PebbleDictionary packet)
    {
        lastSentPacket = (lastSentPacket + 1) % 255;
        Timber.d("SENT %d", lastSentPacket);

        this.lastPacket = packet;

        PebbleKit.sendDataToPebbleWithTransactionId(talkerService, PebbleCompanionApplication.fromContext(talkerService).getPebbleAppUUID(), packet, lastSentPacket);

        commBusy = true;
        retryCount = 0;
    }

    public void sendNext()
    {
        Timber.d("SendNext CommBusy:%b", commBusy);

        if (commBusy)
            return;

        while (!queuedModules.isEmpty())
        {
            CommModule nextModule = queuedModules.peek();

            Timber.d("SendNextModule %s", nextModule.getClass().getSimpleName());

            if (nextModule.sendNextMessage())
                return;

            queuedModules.removeFirst();
        }

        Timber.d("Comm idle!");
    }

    public void receivedAck(int transactionId)
    {
        Timber.d("ACK %d", transactionId);

        if (transactionId != lastSentPacket || lastPacket == null)
        {
            Timber.w("Got invalid ACK");
            return;
        }

        talkerService.getPebbleThreadHandler().removeCallbacks(retryRunnable);

        commBusy = false;
        lastPacket = null;
        sendNext();
    }

    public void receivedNack(int transactionId)
    {
        Timber.d("NACK %d", transactionId);
        if (transactionId != lastSentPacket || lastPacket == null)
        {
            Timber.w("Got invalid NACK");
            return;
        }

        int delayMs = 0;
        if (retryCount == 0)
        {
            // We did not fail yet. Use fairly short delay to let Pebble (Time) app room to breathe and retry.

            Timber.d("Retrying last message for the first time...");
            delayMs = 200;
            retryCount = 1;
        }
        else if (retryCount == 1)
        {
            // We failed once. Try again after longer delay.

            Timber.d("Retrying last message for the second time...");
            delayMs = 600;
            retryCount = 2;
        }
        else
        {
            // We failed twice. App is probably not running or Pebble is not connected. Lets abort this.

            Timber.d("Both retries failed. Aborting send.");
            commBusy = false;
            lastPacket = null;
            return;
        }

        retryHandler.postDelayed(retryRunnable, delayMs);
    }

    public void resetBusy()
    {
        commBusy = false;
        retryHandler.removeCallbacksAndMessages(null);
    }

    public void queueModule(CommModule module)
    {
        if (queuedModules.contains(module))
            queuedModules.remove(module);

        queuedModules.addLast(module);
    }

    public void queueModulePriority(CommModule module)
    {
        if (queuedModules.contains(module))
            queuedModules.remove(module);

        queuedModules.addFirst(module);
    }

    public PebbleCapabilities getConnectedWatchCapabilities()
    {
        return connectedWatchCapabilities;
    }

    public void setConnectedWatchCapabilities(PebbleCapabilities connectedWatchCapabilities)
    {
        this.connectedWatchCapabilities = connectedWatchCapabilities;
        talkerService.getGlobalSettings().edit().putInt("LastWatchCapabilities", connectedWatchCapabilities.serialize());
    }

    public void setConnectedWatchCapabilities(int serializedCapabilities)
    {
        this.connectedWatchCapabilities = PebbleCapabilities.fromSerializedForm(serializedCapabilities);
        talkerService.getGlobalSettings().edit().putInt("LastWatchCapabilities", serializedCapabilities);
    }

    private Runnable retryRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            if (lastPacket == null)
                return;

            lastSentPacket = (lastSentPacket + 1) % 255;
            Timber.d("SENT %d", lastSentPacket);

            PebbleKit.sendDataToPebbleWithTransactionId(talkerService, PebbleCompanionApplication.fromContext(talkerService).getPebbleAppUUID(), lastPacket, lastSentPacket);
        }
    };
}
