package com.example.usb;

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import me.jahnen.libaums.core.UsbMassStorageDevice;
import me.jahnen.libaums.core.fs.FileSystem;
import me.jahnen.libaums.core.fs.UsbFile;
import me.jahnen.libaums.core.fs.UsbFileInputStream;
import me.jahnen.libaums.core.fs.UsbFileStreamFactory;
import me.jahnen.libaums.core.partition.Partition;
import me.jahnen.libaums.core.usb.UsbCommunicationFactory;
import me.jahnen.libaums.libusbcommunication.LibusbCommunicationCreator;

public class MainActivity extends AppCompatActivity {
    private UsbManager mUsbManager;
    String TAG = "USB APP";
    private List<UsbDevice> mDetectedDevices;
    private PendingIntent mPermissionIntent;

    private UsbMassStorageDevice mUsbMSDevice;
    private static final String ACTION_USB_PERMISSION = "com.sportquantum.USB_PERMISSION";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Log.d(TAG, "-----------------------------------------");
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        mUsbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        mDetectedDevices = new ArrayList<UsbDevice>();

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);
       this.registerReceiver(mUsbReceiver, filter);

        checkUSBStatus();
        connectDevice();
    }
    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e(TAG, "Received action " + action);


            checkUSBStatus();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.e(TAG, "USB REMOVED");
            }

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.e(TAG, "USB ATTACHED");
                connectDevice();
            }

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {

                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Log.e(TAG, "WE CAN OPEN DEVICE");
                            openDevice();
                        }
                    } else {
                        Log.e(TAG, "permission denied for device " + device);
                    }
                }
            }

        }
    };

    public void checkUSBStatus() {

        Log.d(TAG, "Check usb status");

        try {
            mDetectedDevices.clear();

            mUsbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);

            if (mUsbManager != null) {
                HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();

                if (!deviceList.isEmpty()) {
                    Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
                    while (deviceIterator.hasNext()) {
                        UsbDevice device = deviceIterator.next();
                        mDetectedDevices.add(device);
                    }
                }

                if (mDetectedDevices.size() > 0) {
                    String deviceName;
                    String serialNumber;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        deviceName = (mDetectedDevices.get(0).getProductName());
                    } else {
                        deviceName = (mDetectedDevices.get(0).getDeviceName());
                    }
                    serialNumber = (mDetectedDevices.get(0).getSerialNumber());

                    Log.d(TAG, "Detected device name: " + deviceName);
                    Log.d(TAG, "Detected device serial number: " + serialNumber);

                }

            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

    }

    public void connectDevice() {
        if (mDetectedDevices.size() > 0) {
            String deviceName;
            String serialNumber;

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                deviceName = (mDetectedDevices.get(0).getProductName());
            } else {
                deviceName = (mDetectedDevices.get(0).getDeviceName());
            }
            serialNumber = (mDetectedDevices.get(0).getSerialNumber());

            Log.d(TAG, "Request connection to device " + deviceName + " n°" + serialNumber);
            UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(this);
            mUsbManager.requestPermission(mDetectedDevices.get(0), mPermissionIntent);
        } else {
            Log.d(TAG, "No device found");
        }
    }

    public void openDevice() {
        if (mDetectedDevices.size() > 0) {
            Log.d(TAG, "openDevice");
            UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(this);

            if (devices.length > 0) {
                mUsbMSDevice = devices[0];

                String deviceName;
                String serialNumber;

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    deviceName = (mDetectedDevices.get(0).getProductName());
                } else {
                    deviceName = (mDetectedDevices.get(0).getDeviceName());
                }
                Log.d(TAG, "openDevice: " + deviceName);
            } else {
                Log.d(TAG, "openDevice: no device");
                return;
            }

            try {
                if(mUsbManager.hasPermission(mUsbMSDevice.getUsbDevice())) {
                    Log.d(TAG, "openDevice: has permissions to open ");
                    mUsbMSDevice.init();

                    UsbDevice device = mUsbMSDevice.getUsbDevice();
                    UsbDeviceConnection usbConnection = mUsbManager.openDevice(device);
                    UsbInterface usbInterface = device.getInterface(0);
                    boolean claimed = usbConnection.claimInterface(usbInterface, true);

                    Log.d(TAG, "openDevice: " + claimed);

                    List<Partition> partitions = mUsbMSDevice.getPartitions();

                    if(partitions.size() != 0) {
                        Log.d(TAG, "openDevice: number of partitions " + partitions.size());
                    } else {
                        Log.d(TAG, "openDevice: no partition found");
                        return;
                    }

                    FileSystem currentFs = partitions.get(0).getFileSystem();
                    readFs(currentFs);

                } else {
                    Log.d(TAG, "openDevice: no permissions");
                }

            } catch (Exception e) {
                Log.e(TAG, "openDevice error: " + e.toString());
                e.printStackTrace();
            }

        }
    }

    private void readFs(FileSystem fs) throws IOException {
        Log.d(TAG, "Capacity: " + fs.getCapacity());
        Log.d(TAG, "Occupied Space: " + fs.getOccupiedSpace());
        Log.d(TAG, "Free Space: " + fs.getFreeSpace());
        Log.d(TAG, "Chunk size: " + fs.getChunkSize());

        UsbFile root = fs.getRootDirectory();

        UsbFile[] files = root.listFiles();
        for(UsbFile file: files) {
            String fileName = file.getName();
            Log.d(TAG, "File: " + fileName);
            if(!file.isDirectory()) {
                if(fileName.equals("network.ini"))
                    Log.d(TAG, "readFs: network.ini found");

                readFile(file, fs);
            }
        }
    }

    private void readFile(UsbFile file, FileSystem fs) {
        try {
            Log.d(TAG, "readFile: " + file.getName());
            InputStream is = UsbFileStreamFactory.createBufferedInputStream(file, fs);
            byte[] buffer = new byte[fs.getChunkSize()];

            is.read(buffer);
        } catch(IOException e) {
            Log.e(TAG, "readFile: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String convertStreamToString(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mUsbMSDevice.close();
    }
}