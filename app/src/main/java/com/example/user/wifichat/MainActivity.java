package com.example.user.wifichat;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class MainActivity extends ActionBarActivity implements Handler.Callback, WifiP2pManager.ConnectionInfoListener {

    public static final String TAG = "MyChat";
    public static final int MY_CHAT = 0;
    public static final int MSG_READ = 1;
    private WifiP2pManager wifiManager;
    private Channel channel;
    private WiFiDirectBroadcastReceiver receiver;
    private IntentFilter mIntentFilter;
    private Handler handler = new Handler(this);
    public static final int SERVER_PORT = 4545;
    private Chat chat=null;
    private EditText editText;
    private ListView listView;
    ArrayAdapter adapter = null;
    private List<String> items = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editText = (EditText) findViewById(R.id.et_text);
        listView = (ListView) findViewById(R.id.list_view);
        adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, items);
        listView.setAdapter(adapter);
        findViewById(R.id.btn_send).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        if (chat != null) {
                            chat.write(editText.getText().toString()
                                    .getBytes());
                            pushMessage("Me: " + editText.getText().toString());
                            editText.setText("");
                            editText.clearFocus();
                        }
                    }
                });

        wifiManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = wifiManager.initialize(this, getMainLooper(), null);
        receiver = new WiFiDirectBroadcastReceiver(wifiManager, channel, this);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);


    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, mIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_discover_devices) {
            wifiManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Toast.makeText(MainActivity.this, "Running discovery", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFailure(int reason) {
                    Toast.makeText(MainActivity.this, "Failed to start discovery " + reason, Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        Thread chatThread = null;
        InetAddress groupOwnerAddress = info.groupOwnerAddress;
        Log.d(TAG, "onConnectionInfoAvailable: " + groupOwnerAddress.getHostAddress());
        if (info.groupFormed && info.isGroupOwner) {

            Log.d(TAG, "onConnectionInfoAvailable: device is groupOwner - act like server");
            try {
                chatThread = new ServerSocketThread(handler);
                chatThread.start();
            } catch (IOException e) {
                Log.d(TAG,
                        "Failed to create a server thread - " + e.getMessage());
                return;
            }
        } else if (info.groupFormed) {

            Log.d(TAG, "onConnectionInfoAvailable: device is client, connect to group owner");
            chatThread = new ClientSocketThread(handler, info.groupOwnerAddress);
            chatThread.start();
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MY_CHAT:
                chat =(Chat) msg.obj;
                break;
            case MSG_READ:
                byte[] readBuf = (byte[]) msg.obj;
                String readMessage = new String(readBuf, 0, msg.arg1);
                Log.d(TAG, readMessage);
                pushMessage("Received: " + readMessage);
        }
        return true;

    }
    public void pushMessage(String readMessage) {
        adapter.add(readMessage);
        adapter.notifyDataSetChanged();
    }

    public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

        private WifiP2pManager mManager;
        private Channel mChannel;
        private MainActivity mActivity;

        public WiFiDirectBroadcastReceiver(WifiP2pManager manager, Channel channel,
                                           MainActivity activity) {
            super();
            this.mManager = manager;
            this.mChannel = channel;
            this.mActivity = activity;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                // Check to see if Wi-Fi is enabled and notify appropriate activity
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    Toast.makeText(MainActivity.this, "WIFI is ENABLED", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "WIFI is DISABLED ", Toast.LENGTH_SHORT).show();

                }
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                // Call WifiP2pManager.requestPeers() to get a list of current peers
                wifiManager.requestPeers(channel, new WifiP2pManager.PeerListListener() {
                    @Override
                    public void onPeersAvailable(WifiP2pDeviceList peers) {
                        Collection<WifiP2pDevice> deviceList = peers.getDeviceList();
                        //самое простое создать диалог но не правильно. лучший вариант поискать диалог
                        FragmentManager fragmentManager = getFragmentManager();
                        DialogFragment dialog = (DialogFragment) fragmentManager.findFragmentByTag("PeersDialog");
                        if (dialog != null) {
                            dialog.dismiss();
                        }
                        PeersDialog.newInstance(deviceList).show(fragmentManager, "PeersDialog");
                    }
                });
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                // Respond to new connection or disconnections
                if (mManager == null) {
                    return;
                }
                NetworkInfo networkInfo = (NetworkInfo) intent
                        .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if (networkInfo.isConnected()) {
                    // We are connected with the other device, request connection
                    // info to find group owner IP
                    mManager.requestConnectionInfo(mChannel, MainActivity.this);
                }
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                // Respond to this device's wifi state changing
            }
        }
    }

    //не правильно. но нужен доступ к этому методу из диалога
    public void onDeviceSelected(final WifiP2pDevice device) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        wifiManager.connect(channel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this, "Connected to " + device.deviceName, Toast.LENGTH_SHORT).show();
                wifiManager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {

                    }

                    @Override
                    public void onFailure(int reason) {

                    }
                });
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(MainActivity.this, "Failed to connect to  " + device.deviceName, Toast.LENGTH_SHORT).show();
            }
        });
    }

    //должен быть паблик статик и иметь дефолтный конструктор, чтобы не было утечек
    public static class PeersDialog extends DialogFragment {
        public static final String EXTRA_DEVICES = "EXTRA_DEVICES";

        public PeersDialog() {

        }

        public static PeersDialog newInstance(Collection<WifiP2pDevice> deviceList) {
            PeersDialog dialog = new PeersDialog();
            Bundle args = new Bundle();
            WifiP2pDevice[] devices = deviceList.toArray(new WifiP2pDevice[deviceList.size()]);
            args.putParcelableArray(EXTRA_DEVICES, devices);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final WifiP2pDevice[] devices = (WifiP2pDevice[]) getArguments().getParcelableArray(EXTRA_DEVICES);
            String[] names = new String[devices.length];
            for (int i = 0; i < devices.length; i++) {
                WifiP2pDevice device = devices[i];
                names[i] = device.deviceName;
            }

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setItems(names, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //не правильныц метод но простой. если вызовем диалог в другой активити то упадет диалог
                            ((MainActivity) getActivity()).onDeviceSelected(devices[which]);
                            dialog.dismiss();
                        }
                    }).setNegativeButton("Cancel", null).create();

            return dialog;
        }
    }
}
